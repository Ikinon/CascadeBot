package org.cascadebot.cascadebot.commands.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.exceptions.PermissionException
import org.cascadebot.cascadebot.commandmeta.CommandContext
import org.cascadebot.cascadebot.commandmeta.MainCommand
import org.cascadebot.cascadebot.commandmeta.Module
import org.cascadebot.cascadebot.data.managers.LockManager
import org.cascadebot.cascadebot.data.managers.ScheduledActionManager
import org.cascadebot.cascadebot.permissions.CascadePermission
import org.cascadebot.cascadebot.scheduler.ActionType
import org.cascadebot.cascadebot.scheduler.ScheduledAction
import org.cascadebot.cascadebot.utils.DiscordUtils
import org.cascadebot.cascadebot.utils.FormatUtils
import org.cascadebot.cascadebot.utils.ParserUtils
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class TempLockCommand : MainCommand() {
    override fun onCommand(sender: Member, context: CommandContext) {
        if (context.args.isEmpty()) {
            context.uiMessaging.replyUsage()
            return
        }

        val longDuration = ParserUtils.parseTextTime(context.getArg(0), false)
        if (longDuration <= 0) {
            context.typedMessaging.replyDanger(context.i18n("responses.invalid_duration"))
            return
        }

        var channel: TextChannel = context.channel
        if (context.args.size == 3) {
            val tempChannel = DiscordUtils.getTextChannel(context.guild, context.getArg(2))
            if (tempChannel != null) {
                channel = tempChannel
            } else {
                context.typedMessaging.replyDanger(context.i18n("responses.cannot_find_channel_matching", context.getArg(2)))
                return
            }
        }

        val temp: ISnowflake? = if (context.args.size > 1) {
            DiscordUtils.getRole(context.getArg(1), context.guild)
                    ?: DiscordUtils.getMember(context.guild, context.getArg(1))
                    ?: DiscordUtils.getTextChannel(context.guild, context.getArg(1))
                    ?: return context.typedMessaging.replyDanger(context.i18n("commands.lock.invalid_argument", context.getArg(0)))
        } else {
            context.channel
        }

        val toAction = ScheduledAction.LockActionData(channel.idLong, null, 0, 0)

        var name: String? = null
        try {
            when (temp) {
                is Role -> {
                    if (channel.getPermissionOverride(temp)?.denied?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = false
                    if (channel.getPermissionOverride(temp)?.allowed?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = true

                    LockManager.add(channel, temp)
                    channel.manager.putPermissionOverride(temp, null, EnumSet.of(Permission.MESSAGE_WRITE)).queue()

                    toAction.targetRoleID = temp.idLong
                    name = "%s %s".format(context.i18n("arguments.role"), temp.asMention)
                }
                is Member -> {
                    if (channel.getPermissionOverride(temp)?.denied?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = false
                    if (channel.getPermissionOverride(temp)?.allowed?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = true

                    LockManager.add(channel, temp)
                    channel.manager.putPermissionOverride(temp, null, EnumSet.of(Permission.MESSAGE_WRITE)).queue()

                    toAction.targetMemberID = temp.idLong
                    name = "%s %s".format(context.i18n("arguments.member"), temp.user.asMention)
                }
                is TextChannel -> {
                    if (temp.getPermissionOverride(context.guild.publicRole)?.denied?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = false
                    if (temp.getPermissionOverride(context.guild.publicRole)?.allowed?.contains(Permission.MESSAGE_WRITE)!!) toAction.oldPermission = true

                    LockManager.add(temp)
                    temp.manager.putPermissionOverride(context.guild.publicRole, null, EnumSet.of(Permission.MESSAGE_WRITE)).queue()

                    toAction.targetChannelID = temp.idLong
                }
            }
        } catch (e: PermissionException) {
            context.uiMessaging.sendBotDiscordPermError(e.permission)
            return
        }

        ScheduledActionManager.registerScheduledAction(ScheduledAction(
                ActionType.UNLOCK,
                toAction,
                context.guild.idLong,
                context.channel.idLong,
                context.member.idLong,
                Instant.now(),
                longDuration
        ))


        val textDuration = FormatUtils.formatTime(longDuration, context.locale, true).replace("(0[hm])".toRegex(), "") +
                " (" + context.i18n("words.until") + " " + FormatUtils.formatDateTime(OffsetDateTime.now().plus(longDuration, ChronoUnit.SECONDS), context.locale) + ")"
        context.typedMessaging.replySuccess(if (temp is TextChannel) context.i18n("commands.templock.text_success", temp.name, textDuration) else name?.let { context.i18n("commands.templock.success", channel, it, textDuration) })
    }


    override fun command(): String {
        return "templock"
    }

    override fun permission(): CascadePermission? {
        return CascadePermission.of("templock", false, Permission.MANAGE_CHANNEL)
    }

    override fun module(): Module {
        return Module.MODERATION
    }

}