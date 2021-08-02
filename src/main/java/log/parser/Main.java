package log.parser;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws MalformedURLException {
        // constants declaration
        final String filePath = "pfsense_syslog.log";
        final URL host = new URL("http://localhost:8080");
        final String network = "unity";
        final String dbName = "unitydb";
        final int batchSize = 100;

        try {
            // grab the load file in /src/resources
            URL val = Main.class.getClassLoader().getResource(filePath);
            assert val != null;

            File file = new File(val.toURI());
            Scanner sc = new Scanner(file);
            Parser pr = new Parser(2020);
            FlureeDB flureeDB = new FlureeDB(host, network, dbName, batchSize);

            while (sc.hasNextLine()){
                String line = sc.nextLine().trim();
                var result = pr.parse(line);

                // skip non-complying results
                if (result == null) {
                    continue;
                }

                long epoch = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(result.timestamp).getTime();

                String transactPayload = """
                        {
                            "_id":"logs",
                            "datetime":%d,
                            "rule":["rules/ruleNumber", %d],
                            "interface":"%s",
                            "action":"%s",
                            "direction":"%s",
                            "protocol":"%s",
                            "src":"%s",
                            "dest":"%s",
                            "srcPort":%d,
                            "destPort":%d
                        }
                        """;
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
        } catch (IOException | URISyntaxException | ParseException e) {
            e.printStackTrace();
        }
    }
}
