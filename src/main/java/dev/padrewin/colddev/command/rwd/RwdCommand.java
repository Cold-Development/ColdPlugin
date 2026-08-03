package dev.padrewin.colddev.command.rwd;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.command.framework.BaseColdCommand;
import dev.padrewin.colddev.command.framework.CommandContext;
import dev.padrewin.colddev.command.framework.CommandInfo;
import dev.padrewin.colddev.command.framework.annotation.ColdExecutable;
import dev.padrewin.colddev.objects.ColdPluginData;
import dev.padrewin.colddev.utils.HexUtils;
import dev.padrewin.colddev.utils.ColdDevUtils;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class RwdCommand extends BaseColdCommand {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public RwdCommand(ColdPlugin coldPlugin) {
        super(coldPlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("rwd")
                .permission("colddev.rwd")
                .build();
    }

    @ColdExecutable
    public void execute(CommandContext context) {
        List<ColdPluginData> pluginData = this.coldPlugin.getLoadedColdPluginsData();

        Component message = LEGACY.deserialize(HexUtils.colorify(
                ColdDevUtils.PREFIX + "&7Plugins installed using " + ColdDevUtils.GRADIENT + "ColdDev &7by " + ColdDevUtils.GRADIENT + "Cold Development&7. Click to view info: "));

        boolean first = true;
        for (ColdPluginData data : pluginData) {
            if (!first)
                message = message.append(LEGACY.deserialize(HexUtils.colorify("&7, ")));
            first = false;

            String updateVersion = data.updateVersion();
            String website = data.website();

            Component hoverContent = LEGACY.deserialize(HexUtils.colorify("&cVersion: &4" + data.version()))
                    .append(LEGACY.deserialize(HexUtils.colorify("\n&cColdDev Version: &4" + data.coldDevVersion())));
            if (updateVersion != null)
                hoverContent = hoverContent.append(LEGACY.deserialize(HexUtils.colorify("\n&cAn update (&4" + updateVersion + "&c) is available! Click to open the GitHub page.")));

            Component pluginName = LEGACY.deserialize(HexUtils.colorify(ColdDevUtils.GRADIENT + data.name()))
                    .hoverEvent(HoverEvent.showText(hoverContent));

            if (website != null)
                pluginName = pluginName.clickEvent(ClickEvent.openUrl(website));

            message = message.append(pluginName);
        }

        context.getSender().sendMessage(message);
    }

}
