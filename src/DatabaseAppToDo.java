import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * A template for a simple app that interfaces with a database on the class server.
 * This program is complicated by the fact that the class database server is behind a firewall and we are not allowed to
 * connect directly to the MariaDB server running on it. As a workaround, we set up an ssh tunnel (this is the purpose
 * of the DatabaseTunnel class) and then connect through that. In a more normal database application setting (in
 * particular if you are writing a database app that connects to a server running on the same computer) you would not
 * have to bother with the tunnel and could just connect directly.
 */
 
// If you change the name of this class (and you should) you need to change it in at least two other places:
//   - The constructor below
//   - In main(), where the class is instantiated
public class DatabaseAppToDo implements AutoCloseable {

    // Default connection information (most can be overridden with command-line arguments)
    // Change these as needed for your app. (You should create a token for your database and use its username
    // and password here.)
    private static final String DB_NAME = "mtw0110_toDo";
    private static final String DB_USER = "token_b6a6";
    private static final String DB_PASSWORD = "NVIBPGU_PNRdR_8a";

    // You can define queries using static final Strings like this.
    private static final String SQL_QUERY_ALL_TASKS = 
          "SELECT *\n"
        + "FROM Item\n";
    // */
    
    // Use "?" as a placeholder for where you will need to insert values (e.g., user input).
    private static final String SQL_ADD_NEW_TASK =
          "INSERT INTO Item (text, complete) VALUES (?, false)";
    
    private static final String SQL_QUERY_ALL_TASKS_COMPLETE = 
            "SELECT id, text, complete\n"
          + "FROM Item\n"
          + "WHERE complete = true";
    
    private static final String SQL_QUERY_ALL_TASKS_NOT = 
            "SELECT id, text, complete\n"
          + "FROM Item\n"
          + "WHERE complete = false";
    
    private static final String SQL_UPDATE_TASK =
            "UPDATE Item\n"
          + "Set complete = true\n"
          + "WHERE text = ?;";
    
    // If you have a newer version of Java (JDK 15+) you can use text blocks to make this easier:
    /*
    private static final String SQL_QUERY_ALL_EMPLOYEES = """
        SELECT id, name
        FROM Employee
    """;
    // */
    
    // Declare one of these for every query your program will use.
    private PreparedStatement queryAllTasks;
    private PreparedStatement addNewTask;
    private PreparedStatement allComplete;
    private PreparedStatement allNot;
    private PreparedStatement updateTask;

    // Connection information to use
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser, dbPassword;

    // The database connection
    private Connection connection;

    /**
     * Creates an {@code IMDbEpisodeQuery} with the specified connection information.
     * @param sshKeyfile the filename of the private key to use for ssh
     * @param dbName the name of the database to use
     * @param dbUser the username to use when connecting
     * @param dbPassword the password to use when connecting
     * @throws SQLException if unable to connect
     */
    public DatabaseApp(String dbHost, int dbPort, String dbName,
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

        // Prepare the statements (queries) that we will execute
        // One of these lines for each query your program will use
        this.queryAllTasks = this.connection.prepareStatement(SQL_QUERY_ALL_TASKS);
        this.addNewTask = this.connection.prepareStatement(SQL_ADD_NEW_TASK);
        this.allComplete = this.connection.prepareStatement(SQL_QUERY_ALL_TASKS_COMPLETE);
        this.allNot = this.connection.prepareStatement(SQL_QUERY_ALL_TASKS_NOT);
        this.updateTask = this.connection.prepareStatement(SQL_UPDATE_TASK);
    }

    /**
     * Runs the application.
     */
    public void runApp() throws SQLException {
        // The main loop of your program.
        // Take user input here, then call methods that you write below to perform whatever
        // queries/tasks your program needs.
        Scanner in = new Scanner(System.in);
        while (true) {
            queryAllEmployees();
            
            System.out.print("\nEnter the name of a task to add (or hit Enter to quit): ");
            String line = in.nextLine();
            if (line.isBlank())
            	break;
            addNewEmployee(line);
            
            System.out.println();

            System.out.print("\nEnter the name of a task to update (or hit Enter to quit): ");
            line = in.nextLine();
            if (line.isBlank())
            	break;
            queryUpdateTask(line);
            
            System.out.println();
            
            queryAllComplete();
            
            System.out.println();

            queryAllNot();
            
        }
    }
    
    // Add one method here for each database operation your app will perform, then call them from runApp() above

    // An example of a method that runs a query
    public void queryAllEmployees() throws SQLException {
        // Execute the query
        ResultSet results = queryAllTasks.executeQuery();

        // Iterate over each row of the results
        while (results.next()) {
            int id = results.getInt("id");
            String text = results.getString("text");
            boolean complete = results.getBoolean("complete");
            System.out.println("[" + id + "] " + text + " " + complete);
        }
    }
    
    // An example of a method that inserts a new row
    public void addNewEmployee(String name) throws SQLException {
        // Insert the value (id) into the placeholder ("?") in the statement
        // (For some reason, the first placeholder is numbered 1, not 0!)
    	addNewTask.setString(1, name);
        
        // Execute. (Since this is an INSERT statement, not a SELECT, there will be no results -
        // we use execute() instead of executeQuery().)
    	addNewTask.execute();
    }
    
    public void queryAllComplete() throws SQLException {
        // Execute the query
        ResultSet results = allComplete.executeQuery();

        // Iterate over each row of the results
        while (results.next()) {
            int id = results.getInt("id");
            String text = results.getString("text");
            boolean complete = results.getBoolean("complete");
            System.out.println("[" + id + "] " + text + " " + complete);
        }
    }

    public void queryAllNot() throws SQLException {
        // Execute the query
        ResultSet results = allNot.executeQuery();

        // Iterate over each row of the results
        while (results.next()) {
            int id = results.getInt("id");
            String text = results.getString("text");
            boolean complete = results.getBoolean("complete");
            System.out.println("[" + id + "] " + text + " " + complete);
        }
    }
    
    public void queryUpdateTask(String name) throws SQLException {
    	// Insert the value (id) into the placeholder ("?") in the statement
        // (For some reason, the first placeholder is numbered 1, not 0!)
        updateTask.setString(1, name);

        // Execute. (Since this is an INSERT statement, not a SELECT, there will be no results -
        // we use execute() instead of executeQuery().)
        updateTask.execute();
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
             DatabaseApp app = new DatabaseApp(
                "localhost", tunnel.getForwardedPort(), params.get("dbname"),
                params.get("user"), params.get("password")
            )) {
            
            // Run the application
            try {
                app.runApp();
            } catch (SQLException ex) {
                System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
                System.err.println("SQL error when running database app!\n");
                ex.printStackTrace();
                System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
            }
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
            System.err.println("(Is the MySQL connector .jar in the CLASSPATH?)");
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
