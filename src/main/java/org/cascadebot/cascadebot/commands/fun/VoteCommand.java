/*
 * Copyright (c) 2019 CascadeBot. All rights reserved.
 * Licensed under the MIT license.
 */

package org.cascadebot.cascadebot.commands.fun;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Member;
import org.cascadebot.cascadebot.commandmeta.CommandContext;
import org.cascadebot.cascadebot.commandmeta.ICommandMain;
import org.cascadebot.cascadebot.commandmeta.Module;
import org.cascadebot.cascadebot.messaging.MessageType;
import org.cascadebot.cascadebot.messaging.MessagingObjects;
import org.cascadebot.cascadebot.permissions.CascadePermission;
import org.cascadebot.cascadebot.utils.votes.VoteButtonGroup;
import org.cascadebot.cascadebot.utils.votes.VoteButtonGroupBuilder;
import org.cascadebot.cascadebot.utils.votes.VoteMessageType;
import org.cascadebot.cascadebot.utils.votes.VoteResult;

import java.util.HashMap;
import java.util.Map;

public class VoteCommand implements ICommandMain {

    public static Map<Long, VoteButtonGroup> voteMap = new HashMap<>();

    @Override
    public void onCommand(Member sender, CommandContext context) {
        if (context.getArgs().length > 10) {
            context.reply("We can only handle votes with less than 9 options!");
            return;
        }
        VoteButtonGroup voteButtonGroup = voteMap.get(context.getGuild().getIdLong());
        VoteButtonGroupBuilder buttonGroupBuilder = new VoteButtonGroupBuilder(VoteMessageType.NUMBERS).setOptionsAmount(context.getArgs().length - 2);
        buttonGroupBuilder.setPeriodicConsumer((results, message) -> {
            StringBuilder resultsBuilder = new StringBuilder();
            for (VoteResult result : results) {
                resultsBuilder.append(result.getVote()).append(" - ").append(result.getAmount()).append(' ');
            }
            message.editMessage(" Vote\n" + resultsBuilder.toString() + "\nLeading with" + (Integer) results.get(0).getVote()).queue();
        });
        buttonGroupBuilder.setVoteFinishConsumer(voteResults -> {
            if (voteResults.size() != 0) {
                int number = (Integer) voteResults.get(0).getVote();
                context.reply(String.valueOf(number));
            }
        });
        VoteButtonGroup buttonGroup = buttonGroupBuilder.build(sender.getUser().getIdLong(), context.getChannel().getIdLong(), context.getGuild().getIdLong());
        voteMap.put(context.getGuild().getIdLong(), buttonGroup);
        EmbedBuilder embed = MessagingObjects.getMessageTypeEmbedBuilder(MessageType.INFO, sender.getUser());
        //buttonGroupBuilder.setVoteTime()
        embed.setTitle("Vote");
        for (int i = 2; i != context.getArgs().length; ++i) {
            embed.addField("Option " + String.valueOf(i - 1), context.getArg(i), false);
        }
        context.getUIMessaging().sendButtonedMessage(embed.build(), buttonGroup);
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