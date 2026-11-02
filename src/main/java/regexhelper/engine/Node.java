package regexhelper.engine;

import java.util.BitSet;
import java.util.List;

public abstract class Node {

    public final BitSet ends(String text, BitSet from) {
        return ends(text, from, TraceLog.OFF);
    }

    public final BitSet ends(String text, BitSet from, TraceLog log) {
        if (!log.isEnabled()) {
            return compute(text, from, log);
        }
        log.enter(label() + ".ends(от " + from + ")");
        BitSet result = compute(text, from, log);
        log.exit("= " + result);
        return result;
    }

    protected abstract BitSet compute(String text, BitSet from, TraceLog log);

    public abstract String label();

    public List<Node> children() {
        return List.of();
    }

    @FunctionalInterface
    public interface CharPredicate {
        boolean test(char c);
    }

    public static final class CharNode extends Node {
        private final String label;
        private final CharPredicate predicate;

        CharNode(String label, CharPredicate predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet result = new BitSet(text.length() + 1);
            for (int i = from.nextSetBit(0); i >= 0; i = from.nextSetBit(i + 1)) {
                if (i >= text.length()) {
                    if (log.isEnabled()) {
                        log.note("позиция " + i + ": конец текста, не подходит");
                    }
                    continue;
                }
                char ch = text.charAt(i);
                boolean ok = predicate.test(ch);
                if (ok) {
                    result.set(i + 1);
                }
                if (log.isEnabled()) {
                    log.note("позиция " + i + ": '" + ch + "' " + (ok ? "подходит → " + (i + 1) : "не подходит"));
                }
            }
            return result;
        }
    }

    public static final class SeqNode extends Node {
        private final List<Node> items;

        SeqNode(List<Node> items) {
            this.items = items;
        }

        @Override
        public String label() {
            return "Seq";
        }

        @Override
        public List<Node> children() {
            return items;
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet current = from;
            for (Node item : items) {
                current = item.ends(text, current, log);
                if (current.isEmpty()) {
                    if (log.isEnabled()) {
                        log.note("цепочка оборвалась: дальше не проверяем");
                    }
                    break;
                }
            }
            return current;
        }
    }

    public static final class AltNode extends Node {
        private final List<Node> branches;

        AltNode(List<Node> branches) {
            this.branches = branches;
        }

        @Override
        public String label() {
            return "Alt (|)";
        }

        @Override
        public List<Node> children() {
            return branches;
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet result = new BitSet(text.length() + 1);
            for (Node branch : branches) {
                result.or(branch.ends(text, from, log));
            }
            if (log.isEnabled()) {
                log.note("объединение ветвей: " + result);
            }
            return result;
        }
    }

    public static final class StarNode extends Node {
        private final Node inner;

        StarNode(Node inner) {
            this.inner = inner;
        }

        @Override
        public String label() {
            return "Star (*)";
        }

        @Override
        public List<Node> children() {
            return List.of(inner);
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet result = (BitSet) from.clone();
            BitSet frontier = from;
            int round = 1;
            while (true) {
                BitSet next = inner.ends(text, frontier, log);
                next.andNot(result);
                if (log.isEnabled()) {
                    log.note("повтор " + round + ": новые позиции " + next);
                }
                if (next.isEmpty()) {
                    break;
                }
                result.or(next);
                frontier = next;
                round++;
            }
            return result;
        }
    }

    public static final class PlusNode extends Node {
        private final Node inner;
        private final StarNode rest;

        PlusNode(Node inner) {
            this.inner = inner;
            this.rest = new StarNode(inner);
        }

        @Override
        public String label() {
            return "Plus (+)";
        }

        @Override
        public List<Node> children() {
            return List.of(inner);
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet first = inner.ends(text, from, log);
            if (log.isEnabled()) {
                log.note("одно вхождение обязательно: " + first + "; остальные повторы как у *");
            }
            return rest.ends(text, first, log);
        }
    }

    public static final class OptNode extends Node {
        private final Node inner;

        OptNode(Node inner) {
            this.inner = inner;
        }

        @Override
        public String label() {
            return "Opt (?)";
        }

        @Override
        public List<Node> children() {
            return List.of(inner);
        }

        @Override
        protected BitSet compute(String text, BitSet from, TraceLog log) {
            BitSet result = inner.ends(text, from, log);
            result.or(from);
            if (log.isEnabled()) {
                log.note("добавлен вариант без вхождения: " + result);
            }
            return result;
        }
    }
}
