package regexhelper.engine;

public class RegexSyntaxException extends Exception {

    private final int position;

    public RegexSyntaxException(String message, int position) {
        super(message);
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
