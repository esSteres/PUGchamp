package bot;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Scanner;
import java.util.function.BiFunction;

abstract class Command {
    private final boolean MOD_ONLY;
    private final boolean MUTATOR;
    private String usage;

    Command(boolean MOD_ONLY, boolean MUTATOR,  String template, String info) {
        this.MOD_ONLY = MOD_ONLY;
        this.MUTATOR = MUTATOR;
        this.usage = template + "\n" + info;
    }

    boolean executeServerCommand(Scanner args, MessageReceivedEvent message, boolean userAuthorized) {
        if (MOD_ONLY && !userAuthorized) {
            message.getMessage().getChannel().sendMessage("You don't have permission to do that").queue();
            return false;
        } else {
            return this.execute(args, message, this::processServerMessage);
        }
    }

    boolean executeDMCommand(Scanner args, MessageReceivedEvent message) {
        return this.execute(args, message, this::processDM);
    }

    //returns true if a mutating event occurred
    boolean execute (Scanner args, MessageReceivedEvent message,
                     ExceptingBiFunction<Scanner, MessageReceivedEvent, String> processor) {
        args.useDelimiter("\\s*,\\s*");
        args.skip("\\s*");

        try {
            String reply = processor.apply(args, message);
            if (reply != null) {
                message.getChannel().sendMessage(reply).queue();
            }
            return MUTATOR;
        } catch (IllegalCommandArgumentException e) {
            message.getMessage().getChannel().sendMessage(e.getMessage()).queue();
            return false;
        } catch (Exception e) {
            message.getMessage().getChannel().sendMessage(this.getUsage()).queue();
            e.printStackTrace();
            return false;
        }
    }

    //you better override at least one of these, or else your command is pretty much useless
    String processDM (Scanner args, MessageReceivedEvent message) throws Exception {
        return "You can't do that in DMs, use a server";
    }

    String processServerMessage (Scanner args, MessageReceivedEvent message) throws Exception {
        return "You can't do that in a server, use DMs";
    }

    String getUsage() {
        return "Usage: " + this.usage;
    }

    boolean hidden() {
        return this.MOD_ONLY;
    }
}
