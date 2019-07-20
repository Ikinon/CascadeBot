/*
 * Copyright (c) 2019 CascadeBot. All rights reserved.
 * Licensed under the MIT license.
 */

package org.cascadebot.cascadebot.commands.fun;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.cascadebot.cascadebot.CascadeBot;
import org.cascadebot.cascadebot.commandmeta.CommandContext;
import org.cascadebot.cascadebot.commandmeta.ICommandMain;
import org.cascadebot.cascadebot.commandmeta.Module;
import org.cascadebot.cascadebot.messaging.MessageType;
import org.cascadebot.cascadebot.messaging.MessagingObjects;
import org.cascadebot.cascadebot.permissions.CascadePermission;
import org.cascadebot.cascadebot.utils.buttons.Button;
import org.cascadebot.cascadebot.utils.buttons.ButtonGroup;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class VoteCommand implements ICommandMain {

    @Override
    public void onCommand(Member sender, CommandContext context) {
        if (context.getArgs().length > 10) {
            context.reply("We can only handle votes with less than 9 options!");
            return;
        }
        int t = LocalDateTime.now().getSecond();
        Message messages;
        ButtonGroup buttonGroup = new ButtonGroup(sender.getUser().getIdLong(), context.getChannel().getIdLong(), context.getGuild().getIdLong());
        StringBuilder messageBuilder = new StringBuilder();
        for (int e = 2; e < context.getArgs().length; e++) {
            char unicode = (char) (0x0030 + e - 1);
            buttonGroup.addButton(new Button.UnicodeButton(unicode + "\u20E3", (runner, channel, message) -> {
                if (!runner.equals(buttonGroup.getOwner())) {
                    return;
                }
            }));
            messageBuilder.append(unicode).append("\u20E3").append(" - ").append(context.getArg(e)).append("\n");

        }
        EmbedBuilder embedBuilder = MessagingObjects.getMessageTypeEmbedBuilder(MessageType.INFO);
        embedBuilder.setTitle("Vote on: " + context.getArg(0));
        embedBuilder.setDescription(messageBuilder.toString());
        try {
            context.getUIMessaging().sendButtonedMessage(embedBuilder.build(), buttonGroup);

        } catch (PermissionException e) {
            context.getTypedMessaging().replyWarning("We need the permission \"add reactions\" for this");
        }
        while (true) {
            if (LocalDateTime.now().getMinute() == Integer.valueOf(context.getArg(1))) {


                context.getChannel().getMessageById(buttonGroup.getMessageId()).queue(message -> message.delete().queue());
                context.getChannel().getMessageById(buttonGroup.getMessageId()).queue(message -> message.getReactions().size());

                context.reply("The time is f*cking over!");
                break;
            } else continue;
        }

    }

    @Override
    public String command() {
        return "vote";
    }

    @Override
    public Module getModule() {
        return Module.FUN;
    }


    @Override
    public CascadePermission getPermission() {
        return CascadePermission.of("vote", true);
    }
}
