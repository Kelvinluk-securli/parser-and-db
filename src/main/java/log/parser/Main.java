package log.parser;

import picocli.CommandLine;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Scanner;

public class Main {

    public static class CommandOptions {
        @CommandLine.Option(names = {"-p", "--path"}, description = "Path for the log file to parse")
        String filePath = "pfsense_syslog.log";
        @CommandLine.Option(names = {"-H", "--host"}, description = "URL of the FlureeDB database")
        String host = "http://localhost:8080";
        @CommandLine.Option(names = {"-n", "--network"}, description = "Network of the FlureeDB database")
        String network = "unity";
        @CommandLine.Option(names = {"-d", "--name"}, description = "Name of the FlureeDB database")
        String dbName = "unitydb";
        @CommandLine.Option(names = {"-s", "--size"}, description = "Set the batch size for each batch transaction")
        int batchSize = 5000;
        @CommandLine.Option(names = {"-y", "--year"}, description = "The initial year inferred by the program")
        int initYear = 2020;

        @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display help message")
        boolean usageHelpRequested;
    }

    public static void main(String[] args) {
        // parse command line arguments
        CommandOptions commandOptions = new CommandOptions();
        CommandLine commandLine = new CommandLine(commandOptions);
        commandLine.parseArgs(args);

        // display help message
        if (commandLine.isUsageHelpRequested()){
            commandLine.usage(System.out);
            return;
        }

        try {
            final String filePath = commandOptions.filePath;
            final URL host = new URL(commandOptions.host);
            final String network = commandOptions.network;
            final String dbName = commandOptions.dbName;
            final int batchSize = commandOptions.batchSize;
            final int initYear = commandOptions.initYear;

            File file = new File(filePath);
            Scanner sc = new Scanner(file);
            Parser pr = new Parser(initYear);
            FlureeDB flureeDB = new FlureeDB(host, network, dbName, batchSize);

            while (sc.hasNextLine()){
                String line = sc.nextLine().trim();
                var result = pr.parse(line);

                // skip non-complying results
                if (result == null) {
                    continue;
                }

                long epoch = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(result.timestamp).getTime();

//                String transactPayload = """
//                        {
//                            "_id":"logs",
//                            "datetime":%d,
//                            "rule":["rules/ruleNumber", %d],
//                            "interface":"%s",
//                            "action":"%s",
//                            "direction":"%s",
//                            "protocol":"%s",
//                            "src":"%s",
//                            "dest":"%s",
//                            "srcPort":%d,
//                            "destPort":%d
//                        }
//                        """;
                String transactPayload = "{\n" +
                        "\t\"_id\":\"logs\",\n" +
                        "\t\"datetime\":%d,\n" +
                        "\t\"rule\":[\"rules/ruleNumber\", %d],\n" +
                        "\t\"interface\":\"%s\",\n" +
                        "\t\"action\":\"%s\",\n" +
                        "\t\"direction\":\"%s\",\n" +
                        "\t\"protocol\":\"%s\",\n" +
                        "\t\"src\":\"%s\",\n" +
                        "\t\"dest\":\"%s\",\n" +
                        "\t\"srcPort\":%d,\n" +
                        "\t\"destPort\":%d";
                transactPayload = String.format(
                        transactPayload,
                        epoch,
                        result.rule,
                        result.interf,
                        result.action,
                        result.direction,
                        result.protocol,
                        result.src,
                        result.dest,
                        result.srcPort,
                        result.destPort
                );

                flureeDB.transact(transactPayload);
            }
            sc.close();
            flureeDB.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (MalformedURLException e) {
            System.out.println("Malformed host URL");
            e.printStackTrace();
        } catch (IOException | URISyntaxException | ParseException e) {
            e.printStackTrace();
        }
    }
}
