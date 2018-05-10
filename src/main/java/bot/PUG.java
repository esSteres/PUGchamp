package bot;

import net.dv8tion.jda.core.entities.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class PUG {
    private LinkedHashSet<User> players;
    private LinkedHashSet<User> watchers;
    private ZonedDateTime time;
    private String description;
    private User mod;
    private Guild guild;
    private String name;
    private int minutesWarning;
    private Role identifier;
    private ScheduledFuture reminder;

    private static ScheduledExecutorService reminderService = Executors.newScheduledThreadPool(5);

    PUG (ZonedDateTime time, String description, User mod, Guild guild, String name, int minutesWarning, String announcementID, String NO_DM_ID) {
        this.time = time;
        this.description = description;
        this.mod = mod;
        this.guild = guild;
        this.name = name;
        this.minutesWarning = minutesWarning;

        this.players = new LinkedHashSet<>();
        this.watchers = new LinkedHashSet<>();

        players.add(mod);

        this.identifier = guild.getController().createRole().setName("[PUG] " + name).complete();
        guild.getController().addSingleRoleToMember(guild.getMember(mod), identifier).queue();

        this.createReminder();

        //announce in pug-pings
        guild.getTextChannelById(announcementID).sendMessage(
                "Attention @everyone:\n A new PUG, \"" + name + "\", has been created. Use !info " +
                        name + ", [your time zone (optional if you already registered a time zone)] to get " +
                        "information and timing in your time zone, and !join " + name + " or !watch " +
                        name + " to register as a player or watcher."
        ).queue();

        //DM anyone without dont dm me as a role
        for (Member member : guild.getMembers()) {
            if (!member.getRoles().contains(guild.getRoleById(NO_DM_ID)) && !member.getUser().isBot()) {
                member.getUser().openPrivateChannel().queue((PrivateChannel channel) -> channel.sendMessage(
                        "A new PUG, \"" + name + "\", has been created. Use !info " + name + ", " +
                                "[your time zone(optional if you already registered a time zone)] to get " +
                                "information and timing in your time zone, and !join " + name +
                                " or !watch " + name + " to register as a player or watcher."
                ).queue());
            }
        }
    }

    PUG(LinkedHashSet<User> players, LinkedHashSet<User> watchers, ZonedDateTime time, String description, User mod,
               Guild guild, String name, int minutesWarning, Role identifier) {
        this.players = players;
        this.watchers = watchers;
        this.time = time;
        this.description = description;
        this.mod = mod;
        this.guild = guild;
        this.name = name;
        this.minutesWarning = minutesWarning;
        this.identifier = identifier;
        this.createReminder();
    }

    private void createReminder() {
        ZonedDateTime now = ZonedDateTime.now();
        long secondsDifference = ChronoUnit.SECONDS.between(now,
                time.withZoneSameInstant(now.getZone()).minusMinutes(minutesWarning));

        if (secondsDifference > 0) {
            this.reminder = reminderService.schedule(() -> {
                informAllOf(players, "The PUG you registered to play in, \"" + name + "\", is beginning in " +
                        minutesWarning + " minutes.");
                informAllOf(watchers, "The PUG you registered to watch, \"" + name + "\", is beginning in " +
                        minutesWarning + " minutes.");
            }, secondsDifference, TimeUnit.SECONDS);
        }
    }

    // effect: registers the given user as a player in this PUG
    void registerPlayer(User player) {
        this.watchers.remove(player);
        this.players.add(player);
        guild.getController().addSingleRoleToMember(guild.getMember(player), identifier).queue();
        if (this.players.size() == 12) {
            this.mod.openPrivateChannel().queue((PrivateChannel channel) ->
                    channel.sendMessage("Your PUG \"" + name + "\" is now full.").queue());
        }
    }

    // effect: register the given user as a watcher of this PUG
    void registerWatcher(User watcher) {
        this.players.remove(watcher);
        this.watchers.add(watcher);
        guild.getController().addSingleRoleToMember(guild.getMember(watcher), identifier).queue();
    }

    //removes player from this pug entirely, unless they are the mod
    void removePlayer (User player) {
        if (player.equals(mod)) {
            throw new IllegalArgumentException();
        }
        this.players.remove(player);
        this.watchers.remove(player);
        guild.getController().removeSingleRoleFromMember(guild.getMember(player), identifier).queue();
    }

    void changeMod (User newMod) {
        guild.getController().removeSingleRoleFromMember(guild.getMember(mod), identifier).queue();
        this.players.remove(mod);
        this.mod = newMod;
        this.players.add(mod);
        guild.getController().addSingleRoleToMember(guild.getMember(mod), identifier).queue();
    }

    // returns a string that quickly describes this pug, hopefully nicely formatted
    // (for !list)
    String briefInfo(ZoneId zone) {
        return " Mod: " + this.mod.getName() + ", Time: " + this.formatTime(zone) +
                ", Players: " + this.players.size() + ".";
    }

    // returns a string that describes this pug, hopefully nicely formatted
    //  (for !info)
    String fullInfo(ZoneId zone) {
        return "Mod: " + this.mod.getName() + "\n" +
                "Time: " + this.formatTime(zone) + "\n" +
                "Players: " + this.players.size() + " of 12\n" +
                "Watchers: " + this.watchers.size() + "\n" +
                "Description: " + this.description;
    }

    private String formatTime(ZoneId zone) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a z M-d-y");
        ZonedDateTime zonedTime = time.withZoneSameInstant(zone);
        return formatter.format(zonedTime);
    }

    String playerList() {
        String out = "Here is everyone playing in this PUG:";
        for (User u : players) {
            out += "\n - " + guild.getMember(u).getEffectiveName();
        }
        return out;
    }

    String watcherList() {
        String out = "Here is everyone watching this PUG:";
        for (User u : watchers) {
            out += "\n - " + guild.getMember(u).getEffectiveName();
        }
        return out;
    }

    // performs any necessary operations before removing this PUG
    void close() {
        this.identifier.delete().queue();
    }

    //inform watchers and players of this pug's cancellation
    void cancel() {
        this.informAllOf(players, "Unfortunately, the PUG you registered to play in, \"" + name + "\", " +
                "has been cancelled.");
        this.informAllOf(watchers, "Unfortunately, the PUG you have been watching, \"" + name + "\", has " +
                "been cancelled.");

        this.identifier.delete().queue();
    }

    /* changes this pug's time, and informs all players and watchers of the change
     * - via DMs
     *   - remind them they can "join" "watch" or "leave" the PUG via DMs
     * - via the designated [PUG]--pug name-- role
     */
    void reschedule(ZonedDateTime newTime) {
        this.time = newTime;
        if (this.reminder != null) {
            this.reminder.cancel(false);
        }
        this.createReminder();

        this.informAllOf(players, "The PUG you are playing in, \"" + name + "\" has been rescheduled.\n" +
                "Use !info " + name + " [your time zone (optional if you already registered a time zone)] to see " +
                "the new time in your time zone, and !watch " + name + " or !leave " + name +
                " to update your status if this time no longer works for you.");

        this.informAllOf(watchers, "The PUG you are watching, \"" + name + "\" has been rescheduled.\n" +
                "Use !info " + name + " [your time zone (optional if you already registered a time zone)] to see the " +
                "new time in your time zone, and !join " + name + " or !leave " + name +
                " to update your status if this new time changes your availability.");
    }

    private void informAllOf(LinkedHashSet<User> users, String notification) {
        for (User u : users) {
            u.openPrivateChannel().queue((PrivateChannel channel) -> channel.sendMessage(notification).queue());
        }
    }

    SerializablePUG toSerializableForm() {
        LinkedHashSet<String> players = new LinkedHashSet<>();
        for (User u: this.players) {
            players.add(u.getId());
        }

        LinkedHashSet<String> watchers = new LinkedHashSet<>();
        for (User u : this.watchers) {
            watchers.add(u.getId());
        }

        String modID = mod.getId();
        String guildID = guild.getId();
        String identifierID = identifier.getId();

        return new SerializablePUG(players, watchers, time, description, modID, guildID, name, minutesWarning, identifierID);
    }
}
