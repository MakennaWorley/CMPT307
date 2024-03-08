import java.sql.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.*;

/**
 * A simple Java application to display a table of all episodes of a TV series.
 * This program is complicated by the fact that the class database server is behind a firewall and we are not allowed to
 * connect directly to the MariaDB server running on it. As a workaround, we set up an ssh tunnel (this is the purpose
 * of the DatabaseTunnel class) and then connect through that. In a more normal database application setting (in
 * particular if you are writing a database app that connects to a server running on the same computer) you would not
 * have to bother with the tunnel and could just connect directly.
 */
public class IMDbEpisodeQuery implements AutoCloseable {

    // Default connection information (most can be overridden with command-line arguments)
    private static final String DB_NAME = "imdb";
    private static final String DB_USER = "student";
    private static final String DB_PASSWORD = "westmini-cmpt307";

    // The query that will be executed
    private static final String QUERY = 
          "SELECT Episode.primaryTitle, EpisodeOf.seasonNum, EpisodeOf.episodeNum\n"
        + "FROM Title AS Series\n"
            + "INNER JOIN EpisodeOf ON Series.id = EpisodeOf.seriesId\n"
            + "INNER JOIN Title AS Episode ON EpisodeOf.episodeId = Episode.id\n"
        + "WHERE Series.primaryTitle = ?\n"
        + "ORDER BY EpisodeOf.seasonNum, EpisodeOf.episodeNum";

    // Connection information to use
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser, dbPassword;

    // The database connection and prepared statement (query)
    private Connection connection;
    private PreparedStatement query;

    /**
     * Creates an {@code IMDbEpisodeQuery} with the specified connection information.
     * @param sshKeyfile the filename of the private key to use for ssh
     * @param dbName the name of the database to use
     * @param dbUser the username to use when connecting
     * @param dbPassword the password to use when connecting
     * @throws SQLException if unable to connect
     */
    public IMDbEpisodeQuery(String dbHost, int dbPort, String dbName,
            String dbUser, String dbPassword) throws SQLException {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;

        connect();
    }

    private void connect() throws SQLException {
        // URL for connecting to the database: includes host, port, database name,
        // user, password
        final String url = String.format("jdbc:mariadb://%s:%d/%s?user=%s&password=%s",
                dbHost, dbPort, dbName,
                dbUser, dbPassword
        );

        // Attempt to connect, returning a Connection object if successful
        this.connection = DriverManager.getConnection(url);

        // Prepare the statement (query) that we will execute
        this.query = this.connection.prepareStatement(QUERY);
    }

    /**
     * Runs the application's interactive mode, which asks the user for the name of a TV
     * series and then prints the episodes.
     */
    public void runApp() throws SQLException {
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("\nEnter the name of a TV series (or hit Enter to quit): ");
            String line = in.nextLine();
            if (line.isBlank())
                break;
            
            System.out.println("Querying database...");
            queryEpisodes(line);
        }
    }

    /**
     * Queries the database to find all episodes of a TV series.
     * @param name the name of the TV series
     * @throws SQLException if an error occurs while querying the database
     */ 
    public void queryEpisodes(String name) throws SQLException {
        // Set the name of the series we're searching for, then execute the query
        query.setString(1, name);
        ResultSet results = query.executeQuery();

        int resultCount = 0;

        // Iterate over each row of the results
        while (results.next()) {
            ++resultCount;
            String title = results.getString("Episode.primaryTitle");
            int seasonNum = results.getInt("EpisodeOf.seasonNum");
            int episodeNum = results.getInt("EpisodeOf.episodeNum");
            System.out.printf("%02d–%02d %s\n", seasonNum, episodeNum, title);
        }

        System.out.printf("\n%d episodes in total.\n\n", resultCount);
    }

    /**
     * Closes the connection to the database.
     */
    @Override
    public void close() throws SQLException {
        connection.close();
    }

    /**
     * Entry point of the application. Uses command-line parameters to override database
     * connection settings, then invokes runApp().
     */
    public static void main(String... args) {
        // Default connection parameters (can be overridden on command line)
        Map<String, String> params = new HashMap<>(Map.of(
            "dbname", "" + DB_NAME,
            "user", DB_USER,
            "password", DB_PASSWORD
        ));

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

        // Connect to the database. This use of "try" ensures that the database connection
        // is closed, even if an exception occurs while running the app.
        try (DatabaseTunnel tunnel = new DatabaseTunnel();
             IMDbEpisodeQuery app = new IMDbEpisodeQuery(
                "localhost", tunnel.getForwardedPort(), params.get("dbname"),
                params.get("user"), params.get("password")
            )) {
            // Run the interactive mode of the application.
            app.runApp();
        } catch (IOException ex) {
            System.err.println("Error setting up ssh tunnel.");
            ex.printStackTrace();
        } catch (SQLException ex) {
            System.err.println("Error communicating with the database (see full message below).");
            ex.printStackTrace();
            System.err.println("\nParameters used to connect to the database:");
            System.err.printf("\tSSH keyfile: %s\n\tDatabase name: %s\n\tUser: %s\n\tPassword: %s\n\n",
                    params.get("sshkeyfile"), params.get("dbname"),
                    params.get("user"), params.get("password")
            );
            System.err.println("(Is the MariaDB .jar in the CLASSPATH?)");
            System.err.println("(Are the username and password correct?)");
        }
        
    }

    private static void printHelp() {
        System.out.println("Accepted command-line arguments:");
        System.out.println();
        System.out.println("\t-help, -h          display this help text");
        System.out.println("\t-dbname <text>     override name of database to connect to");
        System.out.printf( "\t                   (default: %s)\n", DB_NAME);
        System.out.println("\t-user <text>       override database user");
        System.out.printf( "\t                   (default: %s)\n", DB_USER);
        System.out.println("\t-password <text>   override database password");
        System.out.println();
    }
}
