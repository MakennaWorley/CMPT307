import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class CanvasApiExample {
    private static final String CANVAS_TOKEN_FILE = "token.txt";

    private String canvasToken;
    private HttpClient client = HttpClient.newHttpClient();
    private static final Pattern NEXT_URL_PATTERN = Pattern.compile(
        "<([^>]+)>; rel=\"next\""
    );

    public CanvasApiExample(String canvasToken) {
        this.canvasToken = canvasToken;
    }

    public void runApp() {
        try {
            printCourses();
        } catch (IOException ex) {
            System.err.println("I/O error communicating with API!");
            ex.printStackTrace();
        }
        try {
            listAssignments();
        } catch (IOException ex) {
            System.err.println("Cannot print class assignments!");
            ex.printStackTrace();
        }
        try {
            todoList();
        } catch (IOException ex) {
            System.err.println("Cannot print class assignments!");
            ex.printStackTrace();
        }
    }

    private void printCourses() throws IOException {
        JSONArray courses = fetchAll("https://westminster.instructure.com/api/v1/courses?enrollment_state=active");
        for (int i = 0; i < courses.length(); ++i) {
            JSONObject course = courses.getJSONObject(i);
            String code = course.getString("course_code");
            String name = course.getString("name");
            int urlNumber = course.getInt("id");
            String url = "https://westminster.instructure.com/courses/" + urlNumber;
            System.out.printf("[%s] %s %s\n", code, name, url);
        }
    }

    private void listAssignments() throws IOException {
        JSONArray database = fetchAll("https://westminster.instructure.com/api/v1/courses/3438310/assignments");
        for (int i = 0; i < database.length(); ++i) {
            JSONObject classDatabase = database.getJSONObject(i);
            int id = classDatabase.getInt("id");
            String name = classDatabase.getString("name");
            String due = "There is no due date";
            if (classDatabase.isNull("due_at") == false) {
                due = classDatabase.getString("due_at");
            }
            String url = "https://westminster.instructure.com/courses/3438310/assignments/" + id;
            System.out.printf("[%s] %s %s %s\n", id, name, due, url);
        }
    }

    private void todoList() throws IOException {
        JSONArray database = fetchAll("https://westminster.instructure.com//api/v1/users/self/todo");
        for (int i = 0; i < database.length(); ++i) {
            JSONObject toDo = database.getJSONObject(i);
            JSONObject assignment = toDo.getJSONObject("assignment");
            String name = assignment.getString("name");
            String due = "There is no due date";
            if (toDo.isNull("due_at") == false) {
                due = toDo.getString("due_at");
            }
            System.out.printf("%s %s\n", name, due);
        }
    }

    /*
     * The Canvas API paginates long responses, returning by default up to 10 at a time. When there
     * are more results still to come, it includes an HTTP response header that looks like this:
     *     Link: <https://westminster.instructure.com/api/v1/[more URL here...]>; rel="next"
     * (See the NEXT_URL_PATTERN regex defined above.) The client can then make a request to that
     * URL to get the next page of results.
     *
     * This method handles this pagination, checking for the existence of such a Link header and
     * continuing to make requests until exhausted, returning a JSONArray containing all of the
     * results across however many pages were needed.
     *
     * If the server returned a JSON object { ... } instead of an array [ ... ], then a JSONArray
     * containing a single JSONObject is returned.
     */
    private JSONArray fetchAll(String url) throws IOException {
        JSONArray allResults = new JSONArray();
        try {
            // as long as there is more data to fetch...
            while (url != null) {
                // send the request
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + canvasToken)
                    .build()
                ;

                var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // check whether the response was a JSON object { ... } or array [ ... ]
                JSONArray results;
                if (response.body().startsWith("{")) {
                    // it's an object - make an array and add the object to it
                    results = new JSONArray();
                    results.put(new JSONObject(response.body()));
                } else if (response.body().startsWith("[")) {
                    // it's already an array
                    results = new JSONArray(response.body());
                } else {
                    // it's something else
                    throw new RuntimeException("Response is not JSON! (Did you get the URL right?)");
                }

                // add results of this request to the list of all results
                allResults.putAll(results);

                // check for the existence of a Link: <URL...> rel="next" header and fetch that
                // URL next if so
                url = null;
                var responseHeaders = response.headers().map();
                if (responseHeaders.containsKey("Link")) {
                    for (String value : responseHeaders.get("Link")) {
                        Matcher matcher = NEXT_URL_PATTERN.matcher(value);
                        if (matcher.find()) {
                            url = matcher.group(1);
                            break;
                        }
                    }
                }
            }
        } catch (InterruptedException ex) {
            // this is thrown by client.send(), but since we're not multithreading, we don't need
            // to worry about it!
        }

        return allResults;
    }

    public static void main(String... args) {
        try {
            String canvasToken = Files.readString(Path.of(CANVAS_TOKEN_FILE)).trim();
            CanvasApiExample app = new CanvasApiExample(canvasToken);
            app.runApp();
        } catch (IOException ex) {
            System.err.printf("Unable to read file '%s'\n", CANVAS_TOKEN_FILE);
            System.exit(1);
        }
    }
}