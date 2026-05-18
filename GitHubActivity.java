import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GitHubActivity {

    private final Config config;
    private final ObjectMapper mapper = new ObjectMapper();

    GitHubActivity(Config config) {
        this.config = config;
    }

    record Activity(List<JsonNode> pullRequests, List<JsonNode> issues) {
        String toJson() {
            try {
                var m = new ObjectMapper();
                return m.writerWithDefaultPrettyPrinter().writeValueAsString(
                        java.util.Map.of("pull_requests", pullRequests, "issues", issues));
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    Activity fetch() {
        String since = LocalDate.now().minusDays(config.lookbackDays).format(DateTimeFormatter.ISO_DATE);

        System.out.println("Fetching PRs...");
        List<JsonNode> prs = search("prs", "--author", config.githubUser, "--updated", ">=" + since);

        System.out.println("Fetching issues...");
        List<JsonNode> issues = search("issues", "--author", config.githubUser, "--updated", ">=" + since);

        // Filter out excluded orgs and closed (unmerged) PRs
        prs = prs.stream()
                .filter(p -> !config.excludeOrgs.contains(
                        p.path("repository").path("nameWithOwner").asText("").split("/")[0]))
                .filter(p -> !"closed".equalsIgnoreCase(p.path("state").asText("")))
                .toList();

        issues = issues.stream()
                .filter(i -> !config.excludeOrgs.contains(
                        i.path("repository").path("nameWithOwner").asText("").split("/")[0]))
                .toList();

        System.out.println("Found " + prs.size() + " PRs and " + issues.size() + " issues");
        return new Activity(prs, issues);
    }

    private List<JsonNode> search(String type, String... extraArgs) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("gh");
            cmd.add("search");
            cmd.add(type);
            for (String arg : extraArgs) cmd.add(arg);
            cmd.add("--limit");
            cmd.add("50");
            cmd.add("--json");
            cmd.add("repository,title,state,url,updatedAt,createdAt,closedAt");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return List.of();
            }
            String stdout = new String(p.getInputStream().readAllBytes()).trim();
            if (p.exitValue() != 0) return List.of();

            JsonNode arr = mapper.readTree(stdout);
            if (!arr.isArray()) return List.of();

            List<JsonNode> results = new ArrayList<>();
            for (JsonNode n : arr) results.add(n);
            return results;
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return List.of();
        }
    }
}
