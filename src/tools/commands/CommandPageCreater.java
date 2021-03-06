package commands;

import fr.xephi.authme.command.CommandArgumentDescription;
import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.CommandInitializer;
import fr.xephi.authme.command.CommandPermissions;
import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.permission.PermissionNode;
import utils.FileUtils;
import utils.TagValue.NestedTagValue;
import utils.TagValueHolder;
import utils.ToolTask;
import utils.ToolsConstants;

import java.util.Collection;
import java.util.Scanner;
import java.util.Set;

public class CommandPageCreater implements ToolTask {

    private static final String OUTPUT_FILE = ToolsConstants.DOCS_FOLDER + "commands.md";

    @Override
    public String getTaskName() {
        return "createCommandPage";
    }

    @Override
    public void execute(Scanner scanner) {
        final Set<CommandDescription> baseCommands = CommandInitializer.buildCommands();
        NestedTagValue commandTags = new NestedTagValue();
        addCommandsInfo(commandTags, baseCommands);

        FileUtils.generateFileFromTemplate(
            ToolsConstants.TOOLS_SOURCE_ROOT + "commands/commands.tpl.md",
            OUTPUT_FILE,
            TagValueHolder.create().put("commands", commandTags));
        System.out.println("Wrote to '" + OUTPUT_FILE + "' with " + baseCommands.size() + " base commands.");
    }

    private static void addCommandsInfo(NestedTagValue commandTags, Collection<CommandDescription> commands) {
        for (CommandDescription command : commands) {
            TagValueHolder tags = TagValueHolder.create()
                .put("command", CommandUtils.constructCommandPath(command))
                .put("description", command.getDetailedDescription())
                .put("arguments", formatArguments(command.getArguments()))
                .put("permissions", formatPermissions(command.getCommandPermissions()));
            commandTags.add(tags);

            if (!command.getChildren().isEmpty()) {
                addCommandsInfo(commandTags, command.getChildren());
            }
        }
    }

    private static String formatPermissions(CommandPermissions permissions) {
        if (permissions == null) {
            return "";
        }
        String result = "";
        for (PermissionNode node : permissions.getPermissionNodes()) {
            result += node.getNode() + " ";
        }
        return result.trim();
    }

    private static String formatArguments(Iterable<CommandArgumentDescription> arguments) {
        StringBuilder result = new StringBuilder();
        for (CommandArgumentDescription argument : arguments) {
            String argumentName = argument.isOptional()
                ? "[" + argument.getName() + "]"
                : "&lt;" + argument.getName() + ">";
            result.append(" ").append(argumentName);
        }
        return result.toString();
    }
}
