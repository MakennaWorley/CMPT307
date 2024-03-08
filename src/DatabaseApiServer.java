import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

//http://localhost:8000/api/title/8000 is the link

public class DatabaseApiServer implements AutoCloseable {

    private static final String DB_NAME = "mtw0110_books";
    private static final String DB_USER = "token_36df";
    private static final String DB_PASSWORD = "5vbS5apr_N46ne8T";
    private static final int HTTP_PORT = 8000;

    private static final Pattern GET_TITLE_REQUEST = Pattern.compile(
            "^/api/read/(\\d+)$");

    private static final String SQL_QUERY_GET_TITLE = """
                SELECT Book.title
                FROM Book
                WHERE Book.id = ?
            """;

    private static final Pattern INSERT_TITLE_REQUEST = Pattern.compile(
            "^/api/create/([^/]*)/$");

    private static final String SQL_QUERY_INSERT_BOOK = """
            INSERT INTO Book(title, authorId, year)
            VALUES (?, 6, 0000)
            """;

    private static final Pattern UPDATE_TITLE_REQUEST = Pattern.compile(
            "^/api/update/([^/]*)/(\\d+)$");

    private static final String SQL_QUERY_UPDATE_BOOK = """
            UPDATE Book
            SET title = ?
            WHERE id = ?
            """;

    private static final Pattern DELETE_TITLE_REQUEST = Pattern.compile(
            "^/api/delete/(\\d+)$");

    private static final String SQL_QUERY_DELETE_BOOK = """
            DELETE FROM Book
            WHERE id = ?
            """;

    // Declare one of these for every query your program will use.
    private PreparedStatement queryGetTitle;
    private PreparedStatement queryInsertBook;
    private PreparedStatement queryUpdateBook;
    private PreparedStatement queryDeleteBook;

    // Connection information to use
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser, dbPassword;
    private final int httpPort;

    // The database connection
    private Connection connection;
    // The HTTP server
    private HttpServer server;

    /**
     * Creates an {@code IMDbEpisodeQuery} with the specified connection
     * information.
     * 
     * @param sshKeyfile the filename of the private key to use for ssh
     * @param dbName     the name of the database to use
     * @param dbUser     the username to use when connecting
     * @param dbPassword the password to use when connecting
     * @throws SQLException if unable to connect
     */
    public DatabaseApiServer(String dbHost, int dbPort, String dbName,
            String dbUser, String dbPassword, int httpPort) throws SQLException, IOException {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.httpPort = httpPort;

        connect();

        server = HttpServer.create(new InetSocketAddress(httpPort), 0);
        server.createContext("/api/", this::handleGet);
        server.setExecutor(null);
    }

    private void connect() throws SQLException {
        // URL for connecting to the database: includes host, port, database name,
        // user, password
        final String url = String.format("jdbc:mariadb://%s:%d/%s?user=%s&password=%s",
                dbHost, dbPort, dbName,
                dbUser, dbPassword);

        // Attempt to connect, returning a Connection object if successful
        this.connection = DriverManager.getConnection(url);

        // Prepare the statements (queries) that we will execute
        // One of these lines for each query your program will use
        this.queryGetTitle = this.connection.prepareStatement(SQL_QUERY_GET_TITLE);
        this.queryInsertBook = this.connection.prepareStatement(SQL_QUERY_INSERT_BOOK);
        this.queryUpdateBook = this.connection.prepareStatement(SQL_QUERY_UPDATE_BOOK);
        this.queryDeleteBook = this.connection.prepareStatement(SQL_QUERY_DELETE_BOOK);
    }

    public synchronized void start() throws IOException {
        server.start();
        try {
            wait();
        } catch (InterruptedException ex) {
        }
    }

    @Override
    public void close() throws SQLException {
        server.stop(0);
        connection.close();
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        System.out.printf("%s [%s]\n", exchange.getRequestMethod(), exchange.getRequestURI());
        exchange.getRequestBody().close();

        Matcher m = GET_TITLE_REQUEST.matcher(exchange.getRequestURI().toString());
        Matcher n = INSERT_TITLE_REQUEST.matcher(exchange.getRequestURI().toString());
        Matcher o = UPDATE_TITLE_REQUEST.matcher(exchange.getRequestURI().toString());
        Matcher p = DELETE_TITLE_REQUEST.matcher(exchange.getRequestURI().toString());

        int responseCode = 404;
        String responseString = "HTTP 404";

        if (m.matches()) {
            responseCode = 200;
            exchange.getResponseHeaders().set("Content-type", "application/json");
            responseString = "null";

            try {
                queryGetTitle.setString(1, m.group(1));
                var responses = queryGetTitle.executeQuery();

                JSONObject json = null;
                if (responses.next()) {
                    json = new JSONObject();
                    json.put("Book.title", responses.getString("Book.title"));
                    /*
                     * json.put("primaryTitle", responses.getString("Title.primaryTitle"));
                     * json.put("originalTitle", responses.getString("Title.originalTitle"));
                     * json.put("runtime", responses.getInt("Title.runtime"));
                     * json.put("startYear", responses.getInt("Title.startYear"));
                     * json.put("endYear", responses.getInt("Title.endYear"));
                     * json.put("type", responses.getString("TitleType.text"));
                     */
                    responseString = json.toString();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else if (n.matches()) {
            try {
                queryInsertBook.setString(1, n.group(1));
                queryInsertBook.execute();

                responseString = "book added";
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else if (o.matches()) {
            try {
                queryUpdateBook.setString(1, o.group(1));
                int passInt = Integer.parseInt(o.group(2));
                queryUpdateBook.setInt(2, passInt);
                queryUpdateBook.execute();

                responseString = "book updated";
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else if (p.matches()) {
            try {
                int passInt = Integer.parseInt(p.group(1));
                queryDeleteBook.setInt(1, passInt);
                queryDeleteBook.execute();

                responseString = "book deleted";
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        exchange.sendResponseHeaders(responseCode, responseString.length());
        try (PrintStream out = new PrintStream(exchange.getResponseBody())) {
            out.print(responseString);
        }
    }

    public static void main(String... args) {
        // Default connection parameters (can be overridden on command line)
        Map<String, String> params = new HashMap<>(Map.of(
                "dbname", "" + DB_NAME,
                "user", DB_USER,
                "password", DB_PASSWORD,
                "httpPort", "" + HTTP_PORT));

        boolean printHelp = false;

        // Parse command-line arguments, overriding values in params
        for (int i = 0; i < args.length && !printHelp; ++i) {
            String arg = args[i];
            boolean isLast = (i + 1 == args.length);

            switch (arg) {
                case "-h":
                case "-help":
                    printHelp = true;
                    break;

                case "-dbname":
                case "-user":
                case "-password":
                case "-http-port":
                    if (isLast)
                        printHelp = true;
                    else
                        params.put(arg.substring(1), args[++i]);
                    break;

                default:
                    System.err.println("Unrecognized option: " + arg);
                    printHelp = true;
            }
        }

        // If help was requested, print it and exit
        if (printHelp) {
            printHelp();
            return;
        }

        // Connect to the database. This use of "try" ensures that the database
        // connection
        // is closed, even if an exception occurs while running the app.
        try (DatabaseTunnel tunnel = new DatabaseTunnel();
                DatabaseApiServer app = new DatabaseApiServer(
                        "localhost", tunnel.getForwardedPort(), params.get("dbname"),
                        params.get("user"), params.get("password"),
                        Integer.parseInt(params.get("httpPort")))) {

            // Run the application
            try {
                app.start();
            } catch (IOException ex) {
                System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
                System.err.println("I/O error starting HTTP server\n");
                ex.printStackTrace();
                System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
            }
        } catch (IOException ex) {
            System.err.println("Error setting up ssh tunnel or starting HTTP server.");
            ex.printStackTrace();
        } catch (SQLException ex) {
            System.err.println("Error communicating with the database (see full message below).");
            ex.printStackTrace();
            System.err.println("\nParameters used to connect to the database:");
            System.err.printf("\tSSH keyfile: %s\n\tDatabase name: %s\n\tUser: %s\n\tPassword: %s\n\n",
                    params.get("sshkeyfile"), params.get("dbname"),
                    params.get("user"), params.get("password"));
            System.err.println("(Is the MySQL connector .jar in the CLASSPATH?)");
            System.err.println("(Are the username and password correct?)");
        }

    }

    private static void printHelp() {
        System.out.println("Accepted command-line arguments:");
        System.out.println();
        System.out.println("\t-help, -h          display this help text");
        System.out.println("\t-dbname <text>     override name of database to connect to");
        System.out.printf("\t                   (default: %s)\n", DB_NAME);
        System.out.println("\t-user <text>       override database user");
        System.out.printf("\t                   (default: %s)\n", DB_USER);
        System.out.println("\t-password <text>   override database password");
        System.out.println();
    }
}
