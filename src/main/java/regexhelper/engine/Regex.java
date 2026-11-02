package regexhelper.engine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class Regex {

    public static final class Match {
        private final int start;
        private final int end;

        Match(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    private final Node root;

    private Regex(Node root) {
        this.root = root;
    }

    public static Regex compile(String pattern) throws RegexSyntaxException {
        return compile(pattern, TraceLog.OFF);
    }

    public static Regex compile(String pattern, TraceLog log) throws RegexSyntaxException {
        try {
            return new Regex(RegexParser.parse(pattern, log));
        } catch (RegexSyntaxException e) {
            if (log.isEnabled()) {
                log.note("ОШИБКА: " + e.getMessage() + " (позиция " + (e.getPosition() + 1) + ")");
            }
            throw e;
        }
    }

    public Node getRoot() {
        return root;
    }

    public void traceAt(String text, int start, TraceLog log) {
        BitSet from = new BitSet(text.length() + 1);
        from.set(start);
        root.ends(text, from, log);
    }

    public List<Match> findAll(String text) {
        List<Match> found = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            BitSet from = new BitSet(text.length() + 1);
            from.set(i);
            BitSet ends = root.ends(text, from);
            int best = ends.length() - 1; // самая правая позиция конца
            if (best > i) {
                found.add(new Match(i, best));
                i = best;
            } else {
                i++;
            }
        }
        return found;
    }
}
