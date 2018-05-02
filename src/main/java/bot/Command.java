package bot;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;

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
        args.useDelimiter("\\s*,\\s*");
        args.skip("\\s*");
        if (message.getMember() == null) {
            try {
                String reply = this.processDM(args, message);
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
        else {
            if (this.MOD_ONLY && !verifyUser(message.getMember(), modID)) {
                message.getMessage().getChannel().sendMessage("You don't have permission to do that").queue();
            } else {
                try {
                    String reply = this.processServerMessage(args, message);
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
        }
        return false;
        //if neither of those hit I've fucked up somehow
        //really wish all events for receiving a message implemented some interface, would make this a lot easier
    }

    //you better override at least one of these, or else your command is pretty much useless
    String processDM (Scanner args, MessageReceivedEvent message) throws Exception {
        return "You can't do that in DMs, use a server";
    }

    String processServerMessage (Scanner args, MessageReceivedEvent message) throws Exception {
        return "You can't do that in a server, use DMs";
    }

    boolean verifyUser (Member user, String modID) {
        for (Role r : user.getRoles()) {
            if (r.getId().equals(modID)) {
                return true;
            }
        }
        return false;
    }

    String getUsage() {
        return "Usage: " + this.usage;
    }

    boolean hidden() {
        return this.MOD_ONLY;
    }
}
