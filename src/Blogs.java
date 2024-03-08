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
public class Blogs implements AutoCloseable {

    // Default connection information (most can be overridden with command-line arguments)
    private static final String DB_NAME = "mtw0110_blogs";
    private static final String DB_USER = "token_f7cc";
    private static final String DB_PASSWORD = "gnerz7WpHPGJ22S5";

    // Connection information to use
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser, dbPassword;

    // The database connection and prepared statement (query)
    private Connection connection;
    
    public Blogs(String dbHost, int dbPort, String dbName,
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
    }

    public void runApp() throws SQLException {
        Scanner in = new Scanner(System.in);
        System.out.print("Username: ");
        String username = in.nextLine();
        System.out.print("Password: ");
        String password = in.nextLine();
        in.close();

        // Read the ID number (binary) of the user with the given username and
        // password. (If they don't match, the query will not return any rows.)
        // Note that this query DOES NOT SANITIZE INPUT. DO NOT DO THIS IN A REAL
        // PROGRAM. (The point of this is to illustrate the dangers of not
        // sanitizing!)
        final String LOGIN_QUERY = 
            "SELECT id\n"
            + "FROM User\n"
            + "WHERE username = ?\n"
            + "AND password_hash = UNHEX(SHA2(?, 256))";
        
        var statement = connection.prepareStatement(LOGIN_QUERY);
        statement.setString(1, username);
        statement.setString(2, password);
        var results = statement.executeQuery();
        
        // Since the user ID is stored in binary in the database, we will get a
        // byte array from the ResultSet.
        byte[] userIdBytes = null;
        while (results.next())
            userIdBytes = results.getBytes("id");
        
        // If there were no matching username/passwords, then exit.
        if (userIdBytes == null) {
            System.out.println("Invalid username/password combination. Bye!");
            System.exit(0);
        }
        
        // Then we need to convert the byte array into hexadecimal so that we
        // can send it back to the database.
        String userId = toHex(userIdBytes);
        
        // Select the id and title of all blog posts made by this user. (The UNHEX()
        // converts the hexadecimal string back into binary so that it will match
        // up with the binary user ID.)
        // This query does not sanitize the userId, but since it came from a
        // database query in the first place, it *should* be safe. However,
        // "it *should* be safe" has resulted in many data breaches in the past
        // — so this should really be sanitized too!
        final String POSTS_QUERY =
            "SELECT id, title\n"
            + "FROM BlogPost\n"
            + "WHERE user_id = UNHEX('" + userId + "')";
        
        // In a loop, print out all current posts by the user and prompt for
        // a post ID number to delete.
        while (true) {
            statement = connection.prepareStatement(POSTS_QUERY);
            results = statement.executeQuery();
            
            System.out.println("\nBlog posts:");
            
            // Print out ID and title of each post by this user
            while (results.next()) {
                var id = results.getInt("id");
                var title = results.getString("title");
                System.out.printf("[%04d] %s\n", id, title);
            }
            
            // Which post should we delete?
            System.out.print("\nEnter a post number to delete (or hit Enter to exit): ");
            String postId = in.nextLine();
            if (postId.isBlank())
                break;
            
            // Delete the requested post
            // Note that this query DOES NOT SANITIZE INPUT. DO NOT DO THIS IN A REAL
            // PROGRAM. (The point of this is to illustrate the dangers of not
            // sanitizing!)
            final String DELETE_QUERY =
                "DELETE FROM BlogPost\n"
                + "WHERE user_id = UNHEX('" + userId + "')\n"
                + "AND id = " + postId;
            
            statement = connection.prepareStatement(DELETE_QUERY);
            statement.execute();
            
            System.out.println("Post deleted.");
        }
    }

    /**
     * Closes the connection to the database.
     */
    @Override
    public void close() throws SQLException {
        connection.close();
    }
    
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
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
             var app = new Blogs(
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
