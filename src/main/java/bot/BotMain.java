package bot;

import DiscordCMD.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class BotMain {

    private static Map<String, PUG> pugs;
    private static Map<User, ZoneId> timeZones;

    private static String backupFile = "backup.txt";
    private static String announcementChannel;
    private static String modID;
    private static String announcementRole;
    private static String token;

    private static BotCore bot;

    public static void main(String... args) throws Exception {

        String configPath = "config.json";
        if (args.length > 0) {
            configPath = args[0];
        }

        JSONObject configTemp = null;

        try {
            configTemp = new JSONObject(new JSONTokener(new FileInputStream(configPath)));
            announcementChannel = configTemp.getString("announcementChannel");
            modID = configTemp.getString("modID");
            announcementRole = configTemp.getString("announcementRole");
            token = configTemp.getString("token");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(new JFrame(), "Could not read from config file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        }

        JSONObject config = new JSONObject(configTemp);

        if (config.has("backupFile")) {
            backupFile = config.getString("backupFile");
        }

        JDA api = new JDABuilder(AccountType.BOT).setToken(token).buildAsync();

        bot = new BotCore(api) {
            @Override
            public void onGuildMemberJoin(GuildMemberJoinEvent event) {
                if (config.has("greeting")) {
                    event.getGuild().getSystemChannel().sendMessage(
                            config.getString("greeting").replace("%arrival%", event.getMember().getAsMention())
                                    .replace("%prefix%", this.getPrefix())).queue();
                }

            }

            @Override
            public void onReady(ReadyEvent event) {
                try {
                    readFromBackup(event.getJDA());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        PermissionLevel mod = new PermissionLevel(modID);

        bot.registerCommand(new Command("prefix", "[new prefix]", "Changes the bot's prefix.", mod) {
            @Override
            protected String processMessage(Scanner args, MessageEvent messageEvent) throws IllegalCommandArgumentException {
                if (args.hasNext()) {
                    bot.setPrefix(args.next());
                    backup();
                    return "Prefix changed successfully. The prefix is now: " + bot.getPrefix();
                } else {
                    throw new IllegalCommandArgumentException("Cannot set prefix to nothing.");
                }
            }
        });

        bot.registerCommand(new MessageTypeCommand("create", "[PUG name], [time], [optional: description]",
                "Creates a pug at the given time. Time should be in the format HH:MM [am/pm] [Time Zone (optional " +
                        "if you already registered a time zone)] [optional: MM-DD-YYYY]. Date defaults to today if none " +
                        "present. Year may be left off, defaults to this year. Register your time zone with !timezone.",
                mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String pugName = args.next();

                if (pugs.containsKey(pugName)) {
                    return "You cannot create a pug with the same name as an existing one.";
                }

                ZonedDateTime pugTime = parseTime(args.next(), message.getAuthor());

                String pugDescription = "";
                if (args.hasNext()) {
                    args.skip("\\s*,\\s*");
                    pugDescription = args.nextLine();
                }

                Guild guild = message.getMessage().getGuild();

                PUG newPug = new PUG(pugTime, pugDescription, message.getAuthor(), guild, pugName, 5);
                pugs.put(pugName, newPug);

                String prefix = bot.getPrefix();

                //announce in pug-pings
                guild.getTextChannelById(announcementChannel).sendMessage(
                        "Attention " + guild.getRoleById(announcementRole).getAsMention() + ":\n A new PUG, \"" +
                                pugName + "\", has been created. Use " + prefix + "info " +
                                pugName + ", [your time zone (optional if you already registered a time zone)] to get " +
                                "information and timing in your time zone, and " + prefix + "join " + pugName + " or " +
                                prefix + "watch " + pugName + " to register as a player or watcher."
                ).queue();

                backup();
                return "PUG created successfully.";
            }
        });

        bot.registerCommand(new MessageTypeCommand("cancel", "[PUG name]",
                "Deletes the named PUG and informs its players and watchers of the cancellation. This action is " +
                        "irreversible and currently does not ask \"Are you sure?\" or anything, so be careful.",
                mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                pugs.remove(getPugName(args)).cancel();
                backup();
                return "PUG successfully canceled.";
            }
        });

        bot.registerCommand(new MessageTypeCommand("reschedule", "[PUG name], [new time]",
                "Changes named pug to occur at given time. Informs all players and watchers of the change." +
                        "Time should be in the format HH:MM [am/pm] [Time Zone (optional if you already registered a " +
                        "time zone)] [optional: MM-DD-YYYY]. Date defaults to today if none present. Year may be left " +
                        "off, defaults to this year. Register your time zone with %prefix%timezone.", mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String pugName = getPugName(args);
                pugs.get(pugName).reschedule(parseTime(args.next(), message.getAuthor()));
                backup();
                return "PUG rescheduled successfully";
            }
        });

        bot.registerCommand(new MessageTypeCommand("transfer", "[PUG name], [optional: @user]",
                "Makes the named user the new mod of named PUG, or the command author if no user is named. " +
                        "The user must themselves be a mod.", mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                PUG pug = pugs.get(getPugName(args));
                if (args.hasNext()) {
                    Member newMod = message.getMessage().getMentionedMembers().get(0);
                    if (mod.contains(newMod.getRoles())) {
                        pug.changeMod(newMod.getUser());
                        backup();
                        return "PUG successfully transferred to " + newMod.getEffectiveName() + ".";
                    } else {
                        return "New mod must be a moderator.";
                    }
                } else {
                    pug.changeMod(message.getAuthor());
                    backup();
                    return "PUG successfully transferred to " + message.getMember().getEffectiveName() + ".";
                }
            }
        });

        bot.registerCommand(new MessageTypeCommand("end", "[PUG name]",
                "Ends and deletes the named PUG. Currently just acts like %prefix%cancel except it doesn't notify anyone, " +
                        "but more functionality will be added in the future, hopefully.", mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                pugs.remove(getPugName(args)).end();
                backup();
                return "PUG successfully ended. Thank you for using PUGchamp!";
            }
        });

        bot.registerCommand(new Command("watch", "[PUG name]",
                "Registers you as a watcher of the named pug. You will get updates about the PUG, but will not " +
                        "count toward the required 12 players. Use this if you are interested in playing but aren't " +
                        "sure if you can make it, or could play if the time changes slightly, etc. If you are " +
                        "currently a player in the pug, you will no longer be one.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String pugName = getPugName(args);
                pugs.get(pugName).registerWatcher(message.getAuthor());
                backup();
                return "You are now watching " + pugName;
            }
        });

        bot.registerCommand(new Command("join", "[PUG name]",
                "Registers you as a player in the named PUG. If you are currently a watcher, you will no longer " +
                        "be one.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String pugName = getPugName(args);
                pugs.get(pugName).registerPlayer(message.getAuthor());
                backup();
                return "You are now playing in " + pugName;
            }
        });

        bot.registerCommand(new MessageTypeCommand("add", "@user, [PUG name]",
                "Adds the mentioned user as a player in the named PUG, as though they had typed %prefix%join [PUG name].",
                mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                Member player = message.getMessage().getMentionedMembers().get(0);
                args.next();
                pugs.get(getPugName(args)).registerPlayer(player.getUser());
                backup();
                return "Player added successfully.";
            }
        });

        bot.registerCommand(new Command("leave", "[PUG name]",
                "Removes you from the named PUG. You will no longer get updates, and will no longer count toward " +
                        "the required 12 players if you were a player.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                pugs.get(getPugName(args)).removePlayer(message.getAuthor());
                backup();
                return "Left PUG successfully.";
            }
        });

        bot.registerCommand(new MessageTypeCommand("remove", "@user, [PUG name]",
                "Removes named user from the named PUG, as though they had typed %prefix%leave [PUG name].", mod) {
            @Override
            protected String processServerMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                Member player = message.getMessage().getMentionedMembers().get(0);
                args.next();
                pugs.get(getPugName(args)).removePlayer(player.getUser());
                backup();
                return "Player removed successfully.";
            }
        });

        bot.registerCommand(new Command("list", "[time zone (optional if you already registered a time zone)]",
                "Lists all active pugs, with times converted to given time zone. " +
                        "For more info about a specific PUG, use %prefix%info.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String list = "Here are the currently active PUGs:\n";
                ZoneId zone = getZone(message.getAuthor(), args);

                for (PUG pug : pugs.values()) {
                    list += pug.briefInfo(zone) + "\n";
                }

                return list;
            }
        });

        bot.registerCommand(new Command("info", "[PUG name], [time zone (optional if you already registered a time zone)]",
                "returns info about named pug, with times in given time zone - " +
                        "more detailed than the data from %prefix%list", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                String pug = getPugName(args);
                return pugs.get(pug).fullInfo(getZone(message.getAuthor(), args));
            }
        });

        bot.registerCommand(new Command("players", "[PUG name]",
                "Returns a list of people playing in named PUG.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                return pugs.get(getPugName(args)).playerList();
            }
        });

        bot.registerCommand(new Command("watchers", "[PUG name]",
                "Returns a list of people watching the named PUG.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                return pugs.get(getPugName(args)).watcherList();
            }
        });

        bot.registerCommand(new Command("timezone", "[optional: new time zone]",
                "registers your time zone as the given time zone. Any commands that involve time will use the " +
                        "registered time zone if none is specified. %prefix%timezone on its own will display your currently " +
                        "registered time zone, if you have one.", PermissionLevel.EVERYONE) {
            @Override
            protected String processMessage(Scanner args, MessageEvent message) throws IllegalCommandArgumentException {
                User user = message.getAuthor();
                if (args.hasNext()) {
                    timeZones.put(user, parseZone(args.next()));
                    backup();
                    return "Time zone registered successfully! Make sure to use -DT instead of -ST during daylight " +
                            "savings if you're in a country/state that uses it.";
                } else {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("z");
                    if (timeZones.containsKey(user)) {
                        String zone = formatter.format(ZonedDateTime.now().withZoneSameLocal(
                                timeZones.get(user)));
                        return "Your time zone is currently " + zone;
                    } else {
                        return "You don't have a time zone registered. Do so now! It'll be helpful, trust me.";
                    }
                }
            }
        });

        api.addEventListener(bot);
    }

    private static void backup() {
        try {
            File backup = new File(backupFile);
            ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(backup));

            LinkedHashMap<String, SerializablePUG> spugs = new LinkedHashMap<>();
            for (Map.Entry<String, PUG> pug : pugs.entrySet()) {
                spugs.put(pug.getKey(), pug.getValue().toSerializableForm());
            }

            LinkedHashMap<String, ZoneId> sTimeZones = new LinkedHashMap<>();
            for (Map.Entry<User, ZoneId> userEntry : timeZones.entrySet()) {
                sTimeZones.put(userEntry.getKey().getId(), userEntry.getValue());
            }

            output.writeObject(bot.getPrefix());
            output.writeObject(spugs);
            output.writeObject(sTimeZones);
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("WARNING: Could not back up bot! Data may not be saved if bot gets restarted!");
        }
    }

    private static void readFromBackup(JDA api) throws Exception {
        pugs = new LinkedHashMap<>();
        timeZones = new LinkedHashMap<>();

        File backup = new File(backupFile);
        ObjectInputStream input = new ObjectInputStream(new FileInputStream(backup));

        try {
            String prefix = (String) input.readObject();
            bot.setPrefix(prefix);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("WARNING: Could not read prefix data!");
        }

        try {
            LinkedHashMap<String, SerializablePUG> spugs = (LinkedHashMap<String, SerializablePUG>) input.readObject();
            for (Map.Entry<String, SerializablePUG> spug : spugs.entrySet()) {
                try {
                    pugs.put(spug.getKey(), spug.getValue().toPUG(api));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("WARNING: Could not read pug data!");
        }

        try {
            LinkedHashMap<String, ZoneId> sTimeZones = (LinkedHashMap<String, ZoneId>) input.readObject();
            for (Map.Entry<String, ZoneId> userEntry : sTimeZones.entrySet()) {
                try {
                    timeZones.put(api.getUserById(userEntry.getKey()), userEntry.getValue());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("WARNING: Could not read timezone data!");
        }

        input.close();
    }

    private static ZoneId parseZone(String zone) throws IllegalCommandArgumentException {
        try {
            DateTimeFormatter zoneIntake = DateTimeFormatter.ofPattern("z");
            return ZoneId.from(zoneIntake.parse(zone.toUpperCase()));
        } catch (Exception e) {
            throw new IllegalCommandArgumentException("Incorrectly formatted time zone - remember to use " +
                    "the three-letter version, I need to be able to distinguish between daylight and " +
                    "standard times.");
        }
    }

    private static ZonedDateTime parseTime(String time, User u) throws IllegalCommandArgumentException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h[h]:mm a[ z][ M-d][-yyyy]");
        TemporalAccessor parsedTime = formatter.parse(time.toUpperCase());

        ZoneId zone;
        if (parsedTime.isSupported(ChronoField.OFFSET_SECONDS)) {
            zone = ZoneId.from(parsedTime);
        } else {
            if (timeZones.containsKey(u)) {
                zone = timeZones.get(u);
            } else {
                throw new IllegalCommandArgumentException("No time zone found - register one now with %prefix%timezone.");
            }
        }

        ZonedDateTime zonedTime = ZonedDateTime.of(LocalDate.now(), LocalTime.from(parsedTime), zone);

        if (parsedTime.isSupported(ChronoField.MONTH_OF_YEAR)) {
            zonedTime = zonedTime.withMonth(parsedTime.get(ChronoField.MONTH_OF_YEAR));
            zonedTime = zonedTime.withDayOfMonth(parsedTime.get(ChronoField.DAY_OF_MONTH));
            if (parsedTime.isSupported(ChronoField.YEAR)) {
                zonedTime = zonedTime.withYear(parsedTime.get(ChronoField.YEAR));
            }
        }

        return zonedTime;
    }

    private static String getPugName(Scanner args) throws IllegalCommandArgumentException {
        String givenName = args.next().toLowerCase();
        String pugName = null;
        for (String realPug : pugs.keySet()) {
            if (realPug.toLowerCase().contains(givenName)) {
                if (pugName == null || givenName.toLowerCase().equals(realPug.toLowerCase())) {
                    pugName = realPug;
                } else {
                    throw new IllegalCommandArgumentException("More than one PUG matches that identifier.");
                }
            }
        }
        if (pugName != null) {
            return pugName;
        } else {
            throw new IllegalCommandArgumentException("No PUG with that name.");
        }
    }

    private static ZoneId getZone(User user, Scanner args) throws IllegalCommandArgumentException {
        if (args.hasNext()) {
            return parseZone(args.next());
        } else {
            if (timeZones.containsKey(user)) {
                return timeZones.get(user);
            } else {
                throw new IllegalCommandArgumentException("No time zone found - register one now with %prefix%timezone.");
            }
        }
    }
}
