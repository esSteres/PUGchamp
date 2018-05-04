package bot;

import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Scanner;

abstract class Command {
    private final boolean MOD_ONLY;
    private final boolean MUTATOR;
    private String usage;

    Command(boolean MOD_ONLY, boolean MUTATOR,  String template, String info) {
        this.MOD_ONLY = MOD_ONLY;
        this.MUTATOR = MUTATOR;
        this.usage = template + "\n" + info;
    }

    //returns true if a mutating event occurred
    boolean execute (Scanner args, MessageReceivedEvent message, String modID) {
        if (this.MOD_ONLY && !this.authenticate(message, modID)) {
            message.getChannel().sendMessage("You don't have permission to do that.").queue();
            return false;
        }

        args.useDelimiter("\\s*,\\s*");
        args.skip("\\s*");

        ExceptingBiFunction<Scanner, MessageReceivedEvent, String> processor;
        if (message.getMember() != null) {
            processor = this::processServerMessage;
        } else {
            processor = this::processDM;
        }

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


    boolean authenticate(MessageReceivedEvent event, String modID) {
        if (event.getMember() == null) {
            return false;
        } else {
            for (Role role : event.getMember().getRoles()) {
                if (role.getId().equals(modID)) {
                    return true;
                }
            }
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
