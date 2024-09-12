package dev.padrewin.coldplugin.command.framework.command.reflection;

import dev.padrewin.coldplugin.ColdPlugin;
import dev.padrewin.coldplugin.command.argument.ArgumentHandlers;
import dev.padrewin.coldplugin.command.framework.ArgumentsDefinition;
import dev.padrewin.coldplugin.command.framework.BaseColdCommand;
import dev.padrewin.coldplugin.command.framework.CommandContext;
import dev.padrewin.coldplugin.command.framework.CommandInfo;
import dev.padrewin.coldplugin.command.framework.annotation.ColdExecutable;
import dev.padrewin.coldplugin.command.framework.handler.TestArgumentHandler;
import dev.padrewin.coldplugin.command.framework.model.TestEnum;

public class MixedReflectionCommand extends BaseColdCommand {

    public MixedReflectionCommand(ColdPlugin coldPlugin) {
        super(coldPlugin);
    }

    @ColdExecutable
    public void execute(CommandContext context) {
        throw new IllegalStateException("Should never be called for this command");
    }

    @ColdExecutable
    public void execute(CommandContext context, String object) {
        context.getSender().sendMessage(object);
    }

    @ColdExecutable
    public void execute(CommandContext context, TestEnum value, String object) {
        context.getSender().sendMessage(value.name());
        context.getSender().sendMessage(object);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("test")
                .arguments(ArgumentsDefinition.builder()
                        .optional("arg1", ArgumentHandlers.forEnum(TestEnum.class))
                        .required("arg2", new TestArgumentHandler())
                        .build())
                .build();
    }

}
