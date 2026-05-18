///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS org.eclipse.angus:angus-mail:2.0.3
//SOURCES Config.java
//SOURCES GitHubActivity.java
//SOURCES EmailGenerator.java
//SOURCES Notifier.java

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@Command(name = "weekly-status", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Weekly GitHub status email generator using Claude Code")
public class WeeklyStatus implements Callable<Integer> {

    @Option(names = "--preview", description = "Generate and display the email without sending")
    boolean preview;

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMMM dd");
    private static final DateTimeFormatter DISPLAY_FORMAT_YEAR = DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    @Override
    public Integer call() {
        Config config = Config.load();
        config.validate(!preview);

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(config.lookbackDays);
        String weekStart = weekAgo.format(DISPLAY_FORMAT);
        String weekEnd = today.format(DISPLAY_FORMAT_YEAR);

        System.out.println("Fetching GitHub activity since " + weekAgo + "...");
        GitHubActivity gh = new GitHubActivity(config);
        GitHubActivity.Activity activity = gh.fetch();

        System.out.println("Generating email with Claude...");
        EmailGenerator generator = new EmailGenerator();
        String body = generator.generate(activity.toJson(), config.displayName, weekStart, weekEnd);

        if (body == null) {
            System.err.println("Failed to generate email");
            return 1;
        }

        if (preview) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println(body);
            System.out.println("=".repeat(60));

            try {
                Path previewPath = Path.of("/tmp/weekly-status-preview.txt");
                Files.writeString(previewPath, body);
                System.out.println("\nPreview saved to " + previewPath);
            } catch (IOException ignored) {
            }
        } else {
            System.out.println("Sending email...");
            Notifier notifier = new Notifier(config);
            notifier.send(body, weekStart, weekEnd);
        }

        System.out.println("Done!");
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new WeeklyStatus()).execute(args);
        System.exit(exitCode);
    }
}
