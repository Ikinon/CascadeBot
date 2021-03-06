/*
 * Copyright (c) 2019 CascadeBot. All rights reserved.
 * Licensed under the MIT license.
 */

package com.cascadebot.cascadebot.events;

import com.cascadebot.cascadebot.CascadeBot;
import com.cascadebot.cascadebot.Environment;
import com.cascadebot.cascadebot.commandmeta.CommandContext;
import com.cascadebot.cascadebot.commandmeta.CommandException;
import com.cascadebot.cascadebot.commandmeta.ICommandExecutable;
import com.cascadebot.cascadebot.commandmeta.ICommandMain;
import com.cascadebot.cascadebot.commandmeta.ICommandRestricted;
import com.cascadebot.cascadebot.data.Config;
import com.cascadebot.cascadebot.data.mapping.GuildDataMapper;
import com.cascadebot.cascadebot.data.objects.GuildData;
import com.cascadebot.cascadebot.messaging.Messaging;
import com.cascadebot.cascadebot.messaging.MessagingObjects;
import com.cascadebot.shared.Regex;
import com.cascadebot.shared.utils.ThreadPoolExecutorLogged;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.ArrayUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandListener extends ListenerAdapter {

    private static final ThreadGroup COMMAND_THREADS = new ThreadGroup("Command Threads");
    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final ExecutorService COMMAND_POOL = ThreadPoolExecutorLogged.newFixedThreadPool(5, r ->
            new Thread(COMMAND_THREADS, r, "Command Pool-" + threadCounter.incrementAndGet()), CascadeBot.LOGGER);

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = Regex.MULTISPACE_REGEX.matcher(event.getMessage().getContentRaw()).replaceAll(" ");
        GuildData guildData;
        try {
            guildData = GuildDataMapper.getGuildData(event.getGuild().getIdLong());
            if (guildData == null) {
                // This should *hopefully* never happen but just in case :D
                throw new IllegalStateException(String.format("Guild data for guild ID: %s is null!", event.getGuild().getId()));
            }
        } catch (Exception e) {
            Messaging.sendExceptionMessage(event.getChannel(), "We have failed to process your guild data!", new CommandException(e, event.getGuild(), ""));
            return;
        }

        String prefix = guildData.getPrefix();
        boolean isMention = false;

        String commandWithArgs;
        String trigger;
        String[] args;

        if (message.startsWith(prefix)) {
            commandWithArgs = message.substring(prefix.length()); // Remove prefix from command
            trigger = commandWithArgs.split(" ")[0]; // Get first string before a space
            args = ArrayUtils.remove(commandWithArgs.split(" "), 0); // Remove the command portion of the string
        } else if (guildData.getSettings().isMentionPrefix() && message.startsWith(event.getJDA().getSelfUser().getAsMention())) {
            commandWithArgs = message.substring(event.getJDA().getSelfUser().getAsMention().length()).trim();
            trigger = commandWithArgs.split(" ")[0];
            args = ArrayUtils.remove(commandWithArgs.split(" "), 0);
            isMention = true;
        } else if (message.startsWith(Config.INS.getDefaultPrefix() + "prefix") && !Config.INS.getDefaultPrefix().equals(guildData.getPrefix())) {
            commandWithArgs = message.substring(Config.INS.getDefaultPrefix().length());
            trigger = commandWithArgs.split(" ")[0];
            args = ArrayUtils.remove(commandWithArgs.split(" "), 0);
        } else {
            return;
        }

        try {
            processCommands(event, guildData, trigger, args, isMention);
        } catch (Exception e) {
            Messaging.sendExceptionMessage(
                    event.getChannel(),
                    "There was an error while processing your command!",
                    new CommandException(e, event.getGuild(), trigger));
            return;
        }
    }

    private void processCommands(GuildMessageReceivedEvent event, GuildData guildData, String trigger, String[] args, boolean isMention) {
        ICommandMain cmd = CascadeBot.INS.getCommandManager().getCommand(trigger, event.getAuthor(), guildData);
        if (cmd != null) {
            if (cmd.getModule().isPublicModule() &&
                    !guildData.isModuleEnabled(cmd.getModule())) {
                if (guildData.getSettings().willDisplayModuleErrors() || Environment.isDevelopment()) {
                    EmbedBuilder builder = MessagingObjects.getClearThreadLocalEmbedBuilder();
                    builder.setDescription(String.format("The module `%s` for command `%s` is disabled!", cmd.getModule().toString(), trigger));
                    builder.setTimestamp(Instant.now());
                    builder.setFooter("Requested by " + event.getAuthor().getAsTag(), event.getAuthor().getEffectiveAvatarUrl());
                    Messaging.sendDangerMessage(event.getChannel(), builder, guildData.getSettings().useEmbedForMessages());
                }
                // TODO: Modlog?
                return;
            }
            CommandContext context = new CommandContext(
                    event.getJDA(),
                    event.getChannel(),
                    event.getMessage(),
                    event.getGuild(),
                    guildData,
                    args,
                    event.getMember(),
                    trigger,
                    isMention
            );
            if (args.length >= 1) {
                if (processSubCommands(cmd, args, context)) {
                    return;
                }
            }
            dispatchCommand(cmd, context);
        }
    }

    private boolean processSubCommands(ICommandMain cmd, String[] args, CommandContext parentCommandContext) {
        for (ICommandExecutable subCommand : cmd.getSubCommands()) {
            if (subCommand.command().equalsIgnoreCase(args[0])) {
                CommandContext subCommandContext = new CommandContext(
                        parentCommandContext.getJDA(),
                        parentCommandContext.getChannel(),
                        parentCommandContext.getMessage(),
                        parentCommandContext.getGuild(),
                        parentCommandContext.getData(),
                        ArrayUtils.remove(args, 0),
                        parentCommandContext.getMember(),
                        parentCommandContext.getTrigger() + " " + args[0],
                        parentCommandContext.isMention()
                );
                return dispatchCommand(subCommand, subCommandContext);
            }
        }
        return false;
    }

    private boolean dispatchCommand(final ICommandExecutable command, final CommandContext context) {
        if (!CascadeBot.INS.getPermissionsManager().isAuthorised(command, context.getData(), context.getMember())) {
            if (!(command instanceof ICommandRestricted)) { // Always silently fail on restricted commands, users shouldn't know what the commands are
                if (context.getSettings().willShowPermErrors()) {
                    context.replyDanger("You don't have the permission `%s` to run this command!", command.getPermission().getPermissionNode());
                }
            }
            return false;
        }
        COMMAND_POOL.submit(() -> {
            CascadeBot.LOGGER.info("{}Command {}{} executed by {} with args: {}",
                    (command instanceof ICommandMain ? "" : "Sub"),
                    command.command(),
                    (command.command().equalsIgnoreCase(context.getTrigger()) ? "" : " (Trigger: " + context.getTrigger() + ")"),
                    context.getUser().getAsTag(),
                    Arrays.toString(context.getArgs()));
            try {
                command.onCommand(context.getMember(), context);
            } catch (Exception e) {
                context.replyException("There was an error running the command!", e);
                CascadeBot.LOGGER.error(String.format(
                        "Error in command %s Guild ID: %s User: %s",
                        command.command(), context.getGuild().getId(), context.getMember().getEffectiveName()
                ), e);
            }
        });
        deleteMessages(command, context);
        return true;
    }

    private void deleteMessages(ICommandExecutable command, CommandContext context) {
        if (context.getSettings().willDeleteCommand() && command.deleteMessages()) {
            if (context.getGuild().getSelfMember().hasPermission(context.getChannel(), Permission.MESSAGE_MANAGE)) {
                context.getMessage().delete().queue();
            } else {
                context.getGuild().getOwner().getUser().openPrivateChannel().queue(channel -> channel.sendMessage(
                        "We can't delete guild messages as we won't have the permission manage messages! Please either give me this " +
                                "permission or turn off command message deletion!"
                ).queue(), exception -> {
                    // Sad face :( We'll just let them suffer in silence.
                });
            }
        }
    }

    public static void shutdownCommandPool() {
        COMMAND_POOL.shutdown();
    }


}
