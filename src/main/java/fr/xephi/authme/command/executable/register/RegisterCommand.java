package fr.xephi.authme.command.executable.register;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.CommandService;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.output.MessageKey;
import fr.xephi.authme.security.HashAlgorithm;
import fr.xephi.authme.security.RandomString;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.entity.Player;

import java.util.List;

import static fr.xephi.authme.settings.properties.EmailSettings.RECOVERY_PASSWORD_LENGTH;
import static fr.xephi.authme.settings.properties.RegistrationSettings.ENABLE_CONFIRM_EMAIL;
import static fr.xephi.authme.settings.properties.RegistrationSettings.USE_EMAIL_REGISTRATION;
import static fr.xephi.authme.settings.properties.RestrictionSettings.ENABLE_PASSWORD_CONFIRMATION;

public class RegisterCommand extends PlayerCommand {

    @Override
    public void runCommand(Player player, List<String> arguments, CommandService commandService) {
        if (commandService.getProperty(SecuritySettings.PASSWORD_HASH) == HashAlgorithm.TWO_FACTOR) {
            //for two factor auth we don't need to check the usage
            commandService.getManagement().performRegister(player, "", "");
            return;
        }

        // Ensure that there is 1 argument, or 2 if confirmation is required
        final boolean useConfirmation = isConfirmationRequired(commandService);
        if (arguments.isEmpty() || useConfirmation && arguments.size() < 2) {
            commandService.send(player, MessageKey.USAGE_REGISTER);
            return;
        }

        if (commandService.getProperty(USE_EMAIL_REGISTRATION)) {
            handleEmailRegistration(player, arguments, commandService);
        } else {
            handlePasswordRegistration(player, arguments, commandService);
        }
    }

    @Override
    protected String getAlternativeCommand() {
        return "/authme register <playername> <password>";
    }

    private void handlePasswordRegistration(Player player, List<String> arguments, CommandService commandService) {
        if (commandService.getProperty(ENABLE_PASSWORD_CONFIRMATION) && !arguments.get(0).equals(arguments.get(1))) {
            commandService.send(player, MessageKey.PASSWORD_MATCH_ERROR);
        } else {
            commandService.getManagement().performRegister(player, arguments.get(0), "");
        }
    }

    private void handleEmailRegistration(Player player, List<String> arguments, CommandService commandService) {
        if (commandService.getProperty(EmailSettings.MAIL_ACCOUNT).isEmpty()) {
            player.sendMessage("Cannot register: no email address is set for the server. "
                + "Please contact an administrator");
            ConsoleLogger.showError("Cannot register player '" + player.getName() + "': no email is set "
                + "to send emails from. Please add one in your config at " + EmailSettings.MAIL_ACCOUNT.getPath());
            return;
        }

        final String email = arguments.get(0);
        if (!commandService.validateEmail(email)) {
            commandService.send(player, MessageKey.INVALID_EMAIL);
        } else if (commandService.getProperty(ENABLE_CONFIRM_EMAIL) && !email.equals(arguments.get(1))) {
            commandService.send(player, MessageKey.USAGE_REGISTER);
        } else {
            String thePass = RandomString.generate(commandService.getProperty(RECOVERY_PASSWORD_LENGTH));
            commandService.getManagement().performRegister(player, thePass, email);
        }
    }

    /**
     * Return whether the password or email has to be confirmed.
     *
     * @param commandService The command service
     * @return True if the confirmation is needed, false otherwise
     */
    private boolean isConfirmationRequired(CommandService commandService) {
        return commandService.getProperty(USE_EMAIL_REGISTRATION)
            ? commandService.getProperty(ENABLE_CONFIRM_EMAIL)
            : commandService.getProperty(ENABLE_PASSWORD_CONFIRMATION);
    }
}
