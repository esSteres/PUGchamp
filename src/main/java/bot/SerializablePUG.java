package bot;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;

public class SerializablePUG implements Serializable {
    private LinkedHashSet<String> players;
    private LinkedHashSet<String> watchers;
    private ZonedDateTime time;
    private String description;
    private String mod;
    private String guild;
    private String name;
    private String identifier;

    SerializablePUG(LinkedHashSet<String> players, LinkedHashSet<String> watchers, ZonedDateTime time,
                    String description, String mod, String guild, String name, String identifier) {
        this.players = players;
        this.watchers = watchers;
        this.time = time;
        this.description = description;
        this.mod = mod;
        this.guild = guild;
        this.name = name;
        this.identifier = identifier;
    }

    PUG toPUG (JDA api) {
        LinkedHashSet<User> players = new LinkedHashSet<>();
        for (String uid: this.players) {
            players.add(api.getUserById(uid));
        }

        LinkedHashSet<User> watchers = new LinkedHashSet<>();
        for (String uid : this.watchers) {
            watchers.add(api.getUserById(uid));
        }

        Guild guild = api.getGuildById(this.guild);
        User mod = api.getUserById(this.mod);

        Role identifier = guild.getRoleById(this.identifier);
        return new PUG(players, watchers, time, description, mod, guild, name, identifier);
    }
}
