package log.parser;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class FlureeDB {
    static int nextBlockNum = 0;

    private final CloseableHttpClient client = HttpClients.createDefault();
    private final int BATCH_SIZE;
    private final PrintWriter printWriter;

    private final URL targetAdress;
    private final String network;
    private final String dbName;
    private final int start;
    private final int end;

    private final ArrayList<String> batch = new ArrayList<>();

    public static class EndOfTransactException extends Exception{
        public EndOfTransactException() {
            super();
        }

        public EndOfTransactException(String message) {
            super(message);
        }
    }

    public FlureeDB(URL host, String network, String dbName, int batchSize, int start, int end) throws IOException {
        targetAdress = host;
        this.network = network;
        this.dbName = dbName;
        BATCH_SIZE = batchSize;
        this.start = start;
        this.end = end;
        try {
            FileWriter failedFile = new FileWriter("failed.txt", true);
            printWriter = new PrintWriter(failedFile, true);
        } catch (IOException e) {
            System.out.println("Failed to open the file for writing failed tx block");
            throw e;
        }
    }

    /**
     * Adding the JSON message for transaction into a batch arrayList.
     * Perform transaction only when {@link FlureeDB#flush()} is invoked or when batch size
     * reached the predefined one.
     * @param json
     * @throws IOException
     * @throws URISyntaxException
     */
    public void transact(String json) throws IOException, URISyntaxException, EndOfTransactException {
        batch.add(json);
        if (shouldFlush()) {
            flush();
        }
    }

    public boolean close() throws IOException, URISyntaxException {
        try {
            flush();
        } catch (EndOfTransactException ignored){}

        try {
            client.close();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            printWriter.close();
        }
    }

    private boolean shouldFlush(){
        return batch.size()>=BATCH_SIZE;
    }

    private void writeFailedPayload(String payload, int blockNum) {
        if (printWriter != null) {
            String msg = blockNum + ":\n" + payload;
            printWriter.println(msg);
        } else {
            System.out.println("failed to write failed block to file");
        }
    }

    /**
     * Perform transaction with the database server,
     * drop the whole block no matter if transaction is successful or not
     * @return http status code
     * @throws IOException
     * @throws URISyntaxException
     */
    private int flush() throws IOException, URISyntaxException, EndOfTransactException {
        if (nextBlockNum < start) {
            System.out.println("Block Number " + nextBlockNum++ + "skipped\n");
            batch.clear();
            return 0;
        } else if (end >=0 && nextBlockNum >= end) {
            throw new EndOfTransactException();
        }

        URI uri = new URL(targetAdress, "fdb/" + network + "/" + dbName + "/transact").toURI();
        HttpPost hp = new HttpPost(uri);

        // set timeout = 60 seconds
        RequestConfig.Builder requestConfig = RequestConfig.custom();
        requestConfig.setConnectTimeout(60 * 1000);
        requestConfig.setConnectionRequestTimeout(60 * 1000);
        requestConfig.setSocketTimeout(60 * 1000);

        hp.setConfig(requestConfig.build());

        // prepare payload
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i=0;i<batch.size();++i){
            sb.append(batch.get(i));
            if (i < batch.size() - 1)
                sb.append(",");
        }
        sb.append("]");

        System.out.println(sb.toString().getBytes().length);

        StringEntity payload = new StringEntity(sb.toString());
        hp.setEntity(payload);
        hp.setHeader("Content-type", "application/json");

        System.out.println("Establishing connection...");
        System.out.println("Block Number: " + nextBlockNum);
        long startTime = System.currentTimeMillis();
        try {
            CloseableHttpResponse response = client.execute(hp);
            System.out.println("status code: "+ response.getStatusLine().getStatusCode() + "\nbatchsize: "+ batch.size());
            if (response.getStatusLine().getStatusCode() != 200){
                System.out.println("error: " + new String(response.getEntity().getContent().readAllBytes()));
                writeFailedPayload(sb.toString(), nextBlockNum);
            }
            return response.getStatusLine().getStatusCode();
        } catch (ConnectionPoolTimeoutException |SocketTimeoutException e) {
            e.printStackTrace();
            System.out.println("Client disconnect: status code: " + 408 + "\nbatchsize: " + batch.size());
            writeFailedPayload(sb.toString(), nextBlockNum);
            return 408;
        } catch (ClientProtocolException e) {
            System.out.println("Cannot connect to the fluree server, abort...");
            writeFailedPayload(sb.toString(), nextBlockNum);
            throw e;
        } finally {
            System.out.println("Releasing connection...");
            long endTime = System.currentTimeMillis();
            System.out.printf("Time elapsed: %d\n\n", (endTime-startTime));
            ++nextBlockNum;
            hp.releaseConnection();
            batch.clear();
        }
    }










    public static void main(String[] args) throws IOException, URISyntaxException, EndOfTransactException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost hp = new HttpPost("http://localhost:8080/fdb/health");

        CloseableHttpResponse response = client.execute(hp);
        System.out.println(
            response.getStatusLine().getStatusCode() + "\n" +
                    new String(response.getEntity().getContent().readAllBytes()) + "\n"
        );

        HttpPost hp2 = new HttpPost("http://localhost:8080/fdb/unity/unitydb/query");
        String json = "{\"select\": [\"*\"], \"from\": \"rules\"}";
        StringEntity payload = new StringEntity(json);
        hp2.setEntity(payload);
        hp2.setHeader("Content-type", "application/json");
        CloseableHttpResponse response1 = client.execute(hp2);
        System.out.println(
                response1.getStatusLine().getStatusCode() + "\n" +
                        new String(response1.getEntity().getContent().readAllBytes()) + "\n"
        );

        FlureeDB flureeDB = new FlureeDB(new URL("http://localhost:8080"), "unity", "unitydb", 100, 0, -1);
        flureeDB.transact("[{\"_id\":\"rules\", \"ruleNumber\": 0, \"description\":\"test\", \"ruleName\":\"test\"}]");
    }
}
