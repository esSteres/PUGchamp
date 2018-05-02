package bot;

import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Scanner;

abstract class UserInfoCommand extends Command {
    UserInfoCommand(boolean MOD_ONLY, boolean MUTATOR, String template, String info) {
        super (MOD_ONLY, MUTATOR, template, info);
    }

    abstract String processUser (Scanner args, User user) throws Exception;

    @Override
    String processServerMessage(Scanner args, MessageReceivedEvent message) throws Exception {
        return this.processUser(args, message.getAuthor());
    }

    @Override
    String processDM(Scanner args, MessageReceivedEvent message) throws Exception {
        return this.processUser(args, message.getAuthor());
    }
}
