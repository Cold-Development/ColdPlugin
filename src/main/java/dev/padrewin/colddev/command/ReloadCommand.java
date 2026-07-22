package dev.padrewin.colddev.command;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.command.framework.BaseColdCommand;
import dev.padrewin.colddev.command.framework.CommandContext;
import dev.padrewin.colddev.command.framework.CommandInfo;
import dev.padrewin.colddev.command.framework.annotation.ColdExecutable;
import dev.padrewin.colddev.manager.AbstractLocaleManager;

public class ReloadCommand extends BaseColdCommand {

    private final CommandInfo commandInfo;

    public ReloadCommand(ColdPlugin coldPlugin, CommandInfo commandInfo) {
        super(coldPlugin);

        this.commandInfo = commandInfo;
    }

    public ReloadCommand(ColdPlugin coldPlugin) {
        this(coldPlugin, CommandInfo.builder("reload")
                .descriptionKey("command-reload-description")
                .permission(coldPlugin.getName().toLowerCase() + ".reload")
                .build());
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return this.commandInfo;
    }

    @ColdExecutable
    public void execute(CommandContext context) {
        this.coldPlugin.reload(() ->
                this.coldPlugin.getManager(AbstractLocaleManager.class)
                        .sendCommandMessage(context.getSender(), "command-reload-reloaded"));
    }

}
