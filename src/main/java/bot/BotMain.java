package bot;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

public class BotMain {

    public static void main (String... args) throws Exception {
        JDA api = new JDABuilder(AccountType.BOT).setToken(
                "NDQwMDMwNzExOTYyMTQwNjcy.DcbySA.r6QtzUh0lSM-h2_E4bkvQHX-QEg"
        ).buildAsync();

        //testing core init values: "440032519518158848", "440032860720857090", "440032754131009536"
        //real core init values: "438953223714373632", "438954094124728321", "439164999139852308"

        //real core
        BotCore core = new BotCore(
                "438953223714373632", "438954094124728321", "439164999139852308"
                //"440032519518158848", "440032860720857090", "440032754131009536" //tester
        );

        api.addEventListener(core);
    }
}
