package regexhelper.engine;

import regexhelper.engine.Node.AltNode;
import regexhelper.engine.Node.CharNode;
import regexhelper.engine.Node.CharPredicate;
import regexhelper.engine.Node.OptNode;
import regexhelper.engine.Node.PlusNode;
import regexhelper.engine.Node.SeqNode;
import regexhelper.engine.Node.StarNode;

import java.util.ArrayList;
import java.util.List;

final class RegexParser {

    private final String source;
    private final TraceLog log;
    private int pos = 0;

    private RegexParser(String source, TraceLog log) {
        this.source = source;
        this.log = log;
    }

    static Node parse(String source, TraceLog log) throws RegexSyntaxException {
        return new RegexParser(source, log).parseAll();
    }

    private Node parseAll() throws RegexSyntaxException {
        Node node = parseAlt();
        if (pos < source.length()) {
            throw new RegexSyntaxException("лишняя закрывающая скобка", pos);
        }
        return node;
    }

    private boolean more() {
        return pos < source.length();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private Node parseAlt() throws RegexSyntaxException {
        if (log.isEnabled()) {
            log.enter("parseAlt     pos=" + pos);
        }
        Node result = alt();
        if (log.isEnabled()) {
            log.exit("parseAlt → " + result.label());
        }
        return result;
    }

    private Node alt() throws RegexSyntaxException {
        List<Node> branches = new ArrayList<>();
        branches.add(parseSeq());
        while (more() && peek() == '|') {
            pos++;
            branches.add(parseSeq());
        }
        return branches.size() == 1 ? branches.get(0) : new AltNode(branches);
    }

    private Node parseSeq() throws RegexSyntaxException {
        if (log.isEnabled()) {
            log.enter("parseSeq     pos=" + pos);
        }
        Node result = seq();
        if (log.isEnabled()) {
            log.exit("parseSeq → " + result.label());
        }
        return result;
    }

    private Node seq() throws RegexSyntaxException {
        List<Node> items = new ArrayList<>();
        while (more() && peek() != '|' && peek() != ')') {
            items.add(parseRepeat());
        }
        if (items.isEmpty()) {
            throw new RegexSyntaxException("пустая ветвь: ожидался символ, класс или группа", pos);
        }
        return items.size() == 1 ? items.get(0) : new SeqNode(items);
    }

    private Node parseRepeat() throws RegexSyntaxException {
        if (log.isEnabled()) {
            log.enter("parseRepeat  pos=" + pos);
        }
        Node result = repeat();
        if (log.isEnabled()) {
            log.exit("parseRepeat → " + result.label());
        }
        return result;
    }

    private Node repeat() throws RegexSyntaxException {
        Node atom = parseAtom();
        if (more() && isQuantifier(peek())) {
            char quantifier = peek();
            pos++;
            if (quantifier == '*') {
                atom = new StarNode(atom);
            } else if (quantifier == '+') {
                atom = new PlusNode(atom);
            } else {
                atom = new OptNode(atom);
            }
            if (more() && isQuantifier(peek())) {
                throw new RegexSyntaxException("повторный квантификатор (используйте скобки)", pos);
            }
        }
        return atom;
    }

    private static boolean isQuantifier(char c) {
        return c == '*' || c == '+' || c == '?';
    }

    private Node parseAtom() throws RegexSyntaxException {
        if (log.isEnabled()) {
            log.enter("parseAtom    pos=" + pos + "  символ " + show(peek()));
        }
        Node result = atom();
        if (log.isEnabled()) {
            log.exit("parseAtom → " + result.label());
        }
        return result;
    }

    private static String show(char c) {
        switch (c) {
            case '\n': return "'\\n'";
            case '\t': return "'\\t'";
            case '\r': return "'\\r'";
            default: return "'" + c + "'";
        }
    }

    private Node atom() throws RegexSyntaxException {
        char c = peek();
        switch (c) {
            case '(':
                return parseGroup();
            case '[':
                return parseClass();
            case '\\':
                return parseEscape();
            case '.':
                pos++;
                return new CharNode("Any (.)", ch -> true);
            case '*':
            case '+':
            case '?':
                throw new RegexSyntaxException("квантификатор '" + c + "' без операнда", pos);
            default:
                pos++;
                final char literal = c;
                return new CharNode("Char " + show(literal), ch -> ch == literal);
        }
    }

    private Node parseGroup() throws RegexSyntaxException {
        int open = pos;
        pos++; // '('
        Node inner = parseAlt();
        if (!more() || peek() != ')') {
            throw new RegexSyntaxException("не закрыта скобка, открытая в позиции " + (open + 1), open);
        }
        pos++; // ')'
        return inner;
    }

    private Node parseEscape() throws RegexSyntaxException {
        int start = pos;
        pos++; // '\'
        if (!more()) {
            throw new RegexSyntaxException("незавершённая escape-последовательность", start);
        }
        char e = peek();
        pos++;
        CharPredicate shorthand = shorthand(e);
        if (shorthand != null) {
            return new CharNode("Class \\" + e, shorthand);
        }
        final char literal = unescape(e);
        return new CharNode("Char " + show(literal), ch -> ch == literal);
    }

    private Node parseClass() throws RegexSyntaxException {
        int open = pos;
        pos++; // '['
        boolean negate = false;
        if (more() && peek() == '^') {
            negate = true;
            pos++;
        }
        final List<CharPredicate> parts = new ArrayList<>();
        while (true) {
            if (!more()) {
                throw new RegexSyntaxException(
                        "не закрыта квадратная скобка, открытая в позиции " + (open + 1), open);
            }
            if (peek() == ']') {
                pos++;
                break;
            }
            parseClassItem(parts);
        }
        if (parts.isEmpty()) {
            throw new RegexSyntaxException("пустой класс символов []", open);
        }
        final boolean negated = negate;
        return new CharNode("Class " + source.substring(open, pos), ch -> {
            boolean inside = false;
            for (CharPredicate part : parts) {
                if (part.test(ch)) {
                    inside = true;
                    break;
                }
            }
            return inside != negated;
        });
    }

    private void parseClassItem(List<CharPredicate> parts) throws RegexSyntaxException {
        char low;
        if (peek() == '\\') {
            int escapePos = pos;
            pos++;
            if (!more()) {
                throw new RegexSyntaxException("незавершённая escape-последовательность", escapePos);
            }
            char e = peek();
            pos++;
            CharPredicate shorthand = shorthand(e);
            if (shorthand != null) {
                parts.add(shorthand);
                return;
            }
            low = unescape(e);
        } else {
            low = peek();
            pos++;
        }

        boolean isRange = more() && peek() == '-' && pos + 1 < source.length() && source.charAt(pos + 1) != ']';
        if (!isRange) {
            final char single = low;
            parts.add(ch -> ch == single);
            return;
        }

        pos++; // '-'
        char high = peek();
        if (high == '\\') {
            int escapePos = pos;
            pos++;
            if (!more()) {
                throw new RegexSyntaxException("незавершённая escape-последовательность", escapePos);
            }
            high = unescape(peek());
        }
        pos++;
        if (high < low) {
            throw new RegexSyntaxException("некорректный диапазон '" + low + "-" + high + "'", pos - 1);
        }
        final char from = low;
        final char to = high;
        parts.add(ch -> ch >= from && ch <= to);
    }

    private static CharPredicate shorthand(char e) {
        switch (e) {
            case 'd': return Character::isDigit;
            case 'w': return ch -> Character.isLetterOrDigit(ch) || ch == '_';
            case 's': return Character::isWhitespace;
            default: return null;
        }
    }

    private static char unescape(char e) {
        switch (e) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            default: return e;
        }
    }
}
