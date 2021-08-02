package log.parser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Parser {

    public static class info {
        public final String timestamp;
        public final Integer rule;
        public final String interf;
        public final String action;
        public final String direction;
        public final String protocol;
        public final String src;
        public final String dest;
        public final Integer srcPort;
        public final Integer destPort;

        public info(String ts, Integer rule, String interf, String action, String direction, String protocol, String src,
                    String dest, Integer srcPort, Integer destPort) {
            timestamp = ts;
            this.rule = rule;
            this.interf = interf;
            this.action = action;
            this.direction = direction;
            this.protocol = protocol;
            this.src = src;
            this.dest = dest;
            this.srcPort = srcPort;
            this.destPort = destPort;
        }
    }

    int year;
    int currentMonth = 0;
    static final Map<String, Integer> monthMap = new HashMap<>();

    static {
        monthMap.put("Jan", 1);
        monthMap.put("Feb", 2);
        monthMap.put("Mar", 3);
        monthMap.put("Apr", 4);
        monthMap.put("May", 5);
        monthMap.put("Jun", 6);
        monthMap.put("Jul", 7);
        monthMap.put("Aug", 8);
        monthMap.put("Sep", 9);
        monthMap.put("Oct", 10);
        monthMap.put("Nov", 11);
        monthMap.put("Dec", 12);
    }

    public Parser(int initYear) {
        this.year = initYear;
    }

    private String normalizeNumberToTwoDigit(String input) {
        return (input.length() == 2 ? input : "0" + input);
    }

    private String formulateTimestamp(String month, String day, String time) {
        // get the datetime and msg after filterlog
        int intMonth = monthMap.get(month);
        int intDay = Integer.parseInt(day);

        // not chronological order -> next year
        if (intMonth < currentMonth) {
            year++;
        }
        currentMonth = intMonth;

        return year +
                "-" +
                normalizeNumberToTwoDigit(String.valueOf(intMonth)) +
                "-" +
                normalizeNumberToTwoDigit(String.valueOf(intDay)) +
                "T" +
                time;
    }

    private List tokenizeMsg(String msg) {
        var tokenized = msg.split(",");
        Integer rule = Integer.parseInt(tokenized[3]);
        String interf = tokenized[4];
        String action = tokenized[6];
        String dir = tokenized[7];
        String protocol = tokenized[16];
        String src = tokenized[18];
        String dest = tokenized[19];
        Integer srcPort = null;
        Integer destPort = null;
        try {
            srcPort = Integer.parseInt(tokenized[20]);
            destPort = Integer.parseInt(tokenized[21]);
        } catch (NumberFormatException ignored) {
        }

        // abusing raw type :)
        return new ArrayList(Arrays.asList(rule, interf, action, dir, protocol, src, dest, srcPort, destPort));
    }

    public info parse(String line) {
//        Pattern pattern = Pattern.compile("[A-Z][a-z]{2}\\h{1,2}[0-9]{1,2}\\h[0-9]{2}:[0-9]{2}:[0-9]{2}\\h[0-9]{1,3}" +
//                "\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\hfilterlog:\\h\\S+");
        Pattern pattern = Pattern.compile("filterlog:");
        Matcher matcher = pattern.matcher(line);

        if (!matcher.find()){
            // ignored
            return null;
        }

        var tokenized = line.split(" ");
        var tokenizedList = Arrays.stream(tokenized)
                .filter(val -> !val.trim().isEmpty())
                .collect(Collectors.toList());

        String timestamp = formulateTimestamp(tokenizedList.get(0),
                tokenizedList.get(1),
                tokenizedList.get(2));

        var results = tokenizeMsg(tokenizedList.get(5));
        return new info(
            timestamp,
            (Integer) results.get(0),
            (String) results.get(1),
            (String) results.get(2),
            (String) results.get(3),
            (String) results.get(4),
            (String) results.get(5),
            (String) results.get(6),
            (results.get(7) != null) ? (Integer) results.get(7) : null,
            (results.get(8) != null) ? (Integer) results.get(8) : null
        );
    }
}