package bot;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;

public class MessageEvent {
    Message message;
    User author;
    Member member;

    MessageEvent (MessageReceivedEvent mre) {
        this.message = mre.getMessage();
        this.author = mre.getAuthor();
        this.member = mre.getMember();
    }

    MessageEvent (MessageUpdateEvent mue) {
        this.message = mue.getMessage();
        this.author = mue.getAuthor();
        this.member = mue.getMember();
    }

    Message getMessage () {
        return this.message;
    }

    User getAuthor () {
        return this.author;
    }

    Member getMember () {
        return this.member;
    }
}
