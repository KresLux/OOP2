package regexhelper.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class TraceLog {

    private static final int MAX_LINES = 3000;
    private static final String INDENT = "  ";

    public static final TraceLog OFF = new TraceLog(false);

    private final boolean enabled;
    private final List<String> lines = new ArrayList<>();
    private int depth = 0;

    private TraceLog(boolean enabled) {
        this.enabled = enabled;
    }

    public static TraceLog create() {
        return new TraceLog(true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    void enter(String text) {
        add(text);
        depth++;
    }

    void exit(String text) {
        add("← " + text);
        if (depth > 0) {
            depth--;
        }
    }

    void note(String text) {
        add(text);
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    private void add(String text) {
        if (!enabled || lines.size() > MAX_LINES) {
            return;
        }
        if (lines.size() == MAX_LINES) {
            lines.add("… журнал обрезан");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append(INDENT);
        }
        lines.add(sb.append(text).toString());
    }
}
