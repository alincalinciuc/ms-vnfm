package org.project.openbaton.cli;

import com.google.gson.Gson;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import org.project.openbaton.cli.model.Command;
import org.project.openbaton.cli.util.PrintFormat;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.annotations.Help;
import org.openbaton.sdk.api.util.AbstractRestAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Created by lto on 14/07/15.
 *
 */
public class NFVOCommandLineInterface {


    private static Logger log = LoggerFactory.getLogger(NFVOCommandLineInterface.class);

    private static final Character mask = '*';
    private static String CONFIGURATION_FILE = "/etc/openbaton/cli.properties";
    private static final String VERSION = "1";

    private final static LinkedHashMap<String, Command> commandList = new LinkedHashMap<>();
    private final static LinkedHashMap<String, String> helpCommandList = new LinkedHashMap<String, String>() {{
        put("help", "Print the usage");
        //put("exit", "Exit the application");
        //put("print properties", "print all the properties");
    }};

    public static void usage() {

        log.info("\n" +
                " _______  _______________   ____________            _________ .____    .___ \n" +
                " \\      \\ \\_   _____/\\   \\ /   /\\_____  \\           \\_   ___ \\|    |   |   |\n" +
                " /   |   \\ |    __)   \\   Y   /  /   |   \\   ______ /    \\  \\/|    |   |   |\n" +
                "/    |    \\|     \\     \\     /  /    |    \\ /_____/ \\     \\___|    |___|   |\n" +
                "\\____|__  /\\___  /      \\___/   \\_______  /          \\______  /_______ \\___|\n" +
                "        \\/     \\/                       \\/                  \\/        \\/    ");
        log.info("Nfvo OpenBaton Command Line Interface");
        System.out.println("/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~/");
        //System.out.println("Usage: java -jar build/libs/neutrino-<$version>.jar");
        System.out.println("Available commands are");
        String format = "%-80s%s%n";
        for (Object entry : helpCommandList.entrySet()) {
            System.out.printf(format, ((Map.Entry) entry).getKey().toString() + ":", ((Map.Entry) entry).getValue().toString());

        }
        System.out.println("/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~/");
    }

    public static void helpUsage(String s) {
        for (Object entry : helpCommandList.entrySet()) {
            String format = "%-80s%s%n";
            if (((Map.Entry) entry).getKey().toString().startsWith(s) || ((Map.Entry) entry).getKey().toString().startsWith(s + "-")) {
                System.out.printf(format, ((Map.Entry) entry).getKey().toString() + ":", ((Map.Entry) entry).getValue().toString());
            }
        }
    }


    private static void helpCommand(String command) {
        Command cmd = commandList.get(command);
        System.out.println();
        System.out.println("/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~/");
        System.out.print("Usage: " + command + " ");
        for (Class c : cmd.getParams()) {
            System.out.print("<" + c.getSimpleName() + ">");
        }
        System.out.println();
        System.out.println();
        String format = "%-80s%s%n";
        System.out.println("Where:");
        for (Class c : cmd.getParams())
            System.out.printf(format, "<" + c.getSimpleName() + ">  is a: ", c.getName());
        System.out.println("/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~/");
    }

    public static void main(String[] args) {

        //log.info("Log class: { " + log.getClass().getName() + " }");


        ConsoleReader reader = getConsoleReader();
        Properties properties = new Properties();

        CONFIGURATION_FILE = args[0];
        File file = new File(CONFIGURATION_FILE);

        String line;
        PrintWriter out = new PrintWriter(reader.getOutput());

        if (file.exists()) {
            try {
                log.trace("File exists");
                properties.load(new FileInputStream(file));
                log.trace("Properties are: " + properties);
            } catch (IOException e) {
                log.warn("Error reading /etc/openbaton/cli.properties, trying with Environment Variables");
                readEnvVars(properties);
            }
        } else {
            log.warn("File [" + CONFIGURATION_FILE + "] not found, looking for Environment Variables");
            readEnvVars(properties);
        }

        getProperty(reader, properties, "nfvo-usr", "");
        getProperty(reader, properties, "nfvo-pwd", "");
        getProperty(reader, properties, "nfvo-ip", "127.0.0.1");
        getProperty(reader, properties, "nfvo-port", "8080");
        getProperty(reader, properties, "nfvo-version", VERSION);

        NFVORequestor nfvo = new NFVORequestor(properties.getProperty("nfvo-usr"), properties.getProperty("nfvo-pwd"), properties.getProperty("nfvo-ip"), properties.getProperty("nfvo-port"), properties.getProperty("nfvo-version"));

        fillCommands(nfvo);

        List<Completer> completors = new LinkedList<Completer>();
        completors.add(new StringsCompleter(helpCommandList.keySet()));
        completors.add(new FileNameCompleter());
//        completors.add(new NullCompleter());

        reader.addCompleter(new ArgumentCompleter(completors));
        reader.setPrompt("\u001B[135m" + properties.get("nfvo-usr") + "@[\u001B[32mopen-baton\u001B[0m]~> ");

        try {
            reader.setPrompt("\u001B[135m" + properties.get("nfvo-usr") + "@[\u001B[32mopen-baton\u001B[0m]~> ");

            //input reader
            String s = "";
            int find = 0;

            for (Object entry : helpCommandList.entrySet()) {
                String format = "%-80s%s%n";
                String search = args[1] + "-";
                if (((Map.Entry) entry).getKey().toString().equals(args[1])) {
                    find++;
                }
            }

            if (find > 0) { //correct comand

                if (args.length > 3) //tree parameters
                {
                    s = args[1] + " " + args[2] + " " + args[3];
                    /*if (!args[1].endsWith("Descriptor") || !args[1].endsWith("Dependency")) {
                        System.out.println("Error: too much arguments");
                        exit(0);
                    }*/

                } else if (args.length > 2) //two parameters
                {
                    if (args[2].equalsIgnoreCase("help")) {
                        helpUsage(args[1]);
                        exit(0);
                    }else if (args[1].endsWith("All")) {
                        System.out.println("Error: too much arguments");
                        exit(0);
                    }

                    s = args[1] + " " + args[2];
                    if (args[1].contains("update")) {
                        System.out.println("Error: no id or object passed");
                        exit(0);
                    }

                    if (args[1].contains("NetworkServiceDescriptor-delete") && !args[1].endsWith("NetworkServiceDescriptor-delete")) {
                        System.out.println("Error: no id of the Descriptor or the Object");
                        exit(0);
                    }

                } else if (args.length > 1) {
                    s = args[1];
                    if (s.equalsIgnoreCase("help")) {
                        usage();
                        exit(0);
                    } else if (s.contains("delete") || s.endsWith("ById") || s.contains("get")) {
                        System.out.println("Error: no id passed");
                        exit(0);
                    } else if (s.contains("create")) {
                        System.out.println("Error: no object or id passed");
                        exit(0);
                    }else if (s.contains("update")) {
                        System.out.println("Error: no id and/or object passed");
                        exit(0);
                    }else if(s.contains("exit")) {
                        exit(0);
                    }

                }
                //execute comand
                try {
                    String result = PrintFormat.printResult(args[1],executeCommand(s));
                    System.out.println(result);
                    exit(0);

                } catch (Exception e)
                {
                    e.printStackTrace();
                    log.error("Error while invoking command");
                    exit(0);
                }
            } else { //wrong comand
                for (Object entry : helpCommandList.entrySet()) {
                    String format = "%-80s%s%n";
                    if (((Map.Entry) entry).getKey().toString().startsWith(args[1])) {
                        System.out.printf(format, ((Map.Entry) entry).getKey().toString() + ":", ((Map.Entry) entry).getValue().toString());
                        find++;
                    }
                }
                if (find == 0) {

                    System.out.println("Nfvo OpenBaton Command Line Interface");
                    System.out.println("/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~/");
                    System.out.println(args[1] + ": comand not found");
                    exit(0);
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    private static void getProperty(ConsoleReader reader, Properties properties, String property, String defaultProperty) {
        if (properties.get(property) == null) {
            log.warn(property + " property was not found neither in the file [" + CONFIGURATION_FILE + "] nor in Environment Variables");
            try {
                String insertedProperty = reader.readLine(property + "[" + defaultProperty + "]: ");
                if (insertedProperty == null || insertedProperty.equals("")) {
                    insertedProperty = defaultProperty;
                }
                properties.put(property, insertedProperty);

            } catch (IOException e) {
                log.error("Oops, Error while reading from input");
                exit(990);
            }
        }
    }

    private static Object executeCommand(String line) throws InvocationTargetException, IllegalAccessException, FileNotFoundException {
        StringBuffer sb = new StringBuffer();
        StringTokenizer st = new StringTokenizer(line);
        sb.append(st.nextToken());
        log.trace(sb.toString());
        String commandString = sb.toString();
        Command command = commandList.get(commandString);
        if (command == null) {
            return "Command: " + commandString + " not found!";
        }
        log.trace("invoking method: " + command.getMethod().getName() + " with parameters: " + command.getParams());
        List<Object> params = new LinkedList<>();
        for (Type t : command.getParams()) {
            log.trace("type is: " + t.getClass().getName());
            if (t.equals(String.class)) { //for instance an id
                params.add(st.nextToken());
            } else {// for instance waiting for an obj so passing a file
                String pathname = st.nextToken();
                log.trace("the path is: " + pathname);
                File f = new File(pathname);
                Gson gson = new Gson();
                FileInputStream fileInputStream = new FileInputStream(f);
                String file = getString(fileInputStream);
                log.trace(file);
                log.trace("waiting for an object of type " + command.getClazz().getName());
                Object casted = command.getClazz().cast(gson.fromJson(file, command.getClazz()));
                log.trace("Parameter added is: " + casted);
                params.add(casted);
            }
        }
        String parameters = "";
        for (Object type : params) {
            parameters += type.getClass().getSimpleName() + " ";
        }
        log.trace("invoking method: " + command.getMethod().getName() + " with parameters: " + parameters);
        return command.getMethod().invoke(command.getInstance(), params.toArray());
    }

    private static String getString(FileInputStream fileInputStream) {
        StringBuilder builder = new StringBuilder();
        int ch;
        try {
            while ((ch = fileInputStream.read()) != -1) {
                builder.append((char) ch);
            }
        } catch (IOException e) {
            e.printStackTrace();
            exit(333);
        }
        return builder.toString();
    }


    private static void fillCommands(NFVORequestor nfvo) {
        getMethods(nfvo.getNetworkServiceRecordAgent());
        getMethods(nfvo.getConfigurationAgent());
        getMethods(nfvo.getImageAgent());
        getMethods(nfvo.getEventAgent());
        getMethods(nfvo.getVNFFGAgent());
        getMethods(nfvo.getVimInstanceAgent());
        getMethods(nfvo.getNetworkServiceDescriptorAgent());
        getMethods(nfvo.getVirtualLinkAgent());

    }

    private static void getMethods(AbstractRestAgent agent) {
        String className = agent.getClass().getSimpleName();
        log.trace(className);
        Class clazz = null;
        try {
            clazz = (Class) agent.getClass().getSuperclass().getDeclaredMethod("getClazz").invoke(agent);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            exit(123);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            exit(123);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            exit(123);
        }
        String replacement = null;
        if (className.endsWith("RestRequest")) {
            replacement = className.substring(0, className.indexOf("RestRequest"));
        } else if (className.endsWith("RestAgent")) {
            replacement = className.substring(0, className.indexOf("RestAgent"));
        } else if (className.endsWith("Agent")) {
            replacement = className.substring(0, className.indexOf("Agent"));
        } else
            exit(700);
        log.trace("Clazz: " + clazz);
        log.trace("Replacement: " + replacement);
        for (Method method : agent.getClass().getSuperclass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Help.class)) {
                log.trace("Method: " + method.getName());
                helpCommandList.put(replacement + "-" + method.getName(), method.getAnnotation(Help.class).help().replace("{#}", replacement));
                Command command = new Command(agent, method, method.getParameterTypes(), clazz);
                commandList.put(replacement + "-" + method.getName(), command);
            }
        }
        for (Method method : agent.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Help.class)) {
                Command command = new Command(agent, method, method.getParameterTypes(), clazz);
                commandList.put(replacement + "-" + method.getName(), command);
                helpCommandList.put(replacement + "-" + method.getName(), method.getAnnotation(Help.class).help());
            }
        }
    }

    private static ConsoleReader getConsoleReader() {
        ConsoleReader reader = null;
        try {
            reader = new ConsoleReader();
        } catch (IOException e) {
            log.error("Oops, Error while creating ConsoleReader");
            exit(999);
        }
        return reader;
    }

    private static void readEnvVars(Properties properties) {
        try {
            properties.put("nfvo-usr", System.getenv().get("nfvo_usr"));
            properties.put("nfvo-pwd", System.getenv().get("nfvo_pwd"));
            properties.put("nfvo-ip", System.getenv().get("nfvo_ip"));
            properties.put("nfvo-port", System.getenv().get("nfvo_port"));
            properties.put("nfvo-version", System.getenv().get("nfvo_version"));
        } catch (NullPointerException e) {
            return;
        }
    }

    /**
     * * * EXIT STATUS CODE
     *
     * @param status: *) 1XX Variable errors
     *                *    *) 100: variable not found
     *                <p/>
     *                *) 8XX: reflection Errors
     *                *    *) 801 ConsoleReader not able to read
     *                *) 9XX: Libraries Errors
     *                *    *) 990 ConsoleReader not able to read
     *                *    *) 999: ConsoleReader not created
     */
    private static void exit(int status) {
        System.exit(status);
    }
}