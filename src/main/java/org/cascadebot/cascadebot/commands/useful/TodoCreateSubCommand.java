package org.cascadebot.cascadebot.commands.useful;

import net.dv8tion.jda.api.entities.Member;
import org.cascadebot.cascadebot.commandmeta.CommandContext;
import org.cascadebot.cascadebot.commandmeta.ISubCommand;
import org.cascadebot.cascadebot.permissions.CascadePermission;

public class TodoCreateSubCommand implements ISubCommand {

    @Override
    public void onCommand(Member sender, CommandContext context) {

    }

    @Override
    public String command() {
        return "create";
    }

    @Override
    public String parent() {
        return "todo";
    }

    @Override
    public CascadePermission getPermission() {
        return CascadePermission.of("todo.create", true);
    }

}
