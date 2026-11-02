package regexhelper.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import regexhelper.engine.Node;
import regexhelper.engine.Regex;
import regexhelper.engine.Regex.Match;
import regexhelper.engine.RegexSyntaxException;
import regexhelper.engine.TraceLog;

import java.util.List;

public class RegexHelperApp extends Application {

    private TextField exprField;
    private TextArea textArea;
    private Label statusLabel;
    private ScrollPane scroll;
    private TreeView<String> treeView;
    private TextArea parseLogArea;
    private TextArea walkLogArea;

    @Override
    public void start(Stage stage) {
        Label exprLabel = new Label("Выражение:");
        exprField = new TextField();
        exprField.setPromptText("Например: [a-z]+@(mail|gmail)\\.com");

        Label textLabel = new Label("Строка (текст):");
        textArea = new TextArea();
        textArea.setPromptText("Введите произвольный текст для поиска");
        textArea.setWrapText(true);
        textArea.setPrefRowCount(4);

        Button checkBtn = new Button("Проверить");
        checkBtn.setDefaultButton(true);
        checkBtn.setOnAction(e -> check());

        statusLabel = new Label(" ");
        statusLabel.setWrapText(true);

        scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        showFlow(new TextFlow());

        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        parseLogArea = logArea();
        walkLogArea = logArea();

        TabPane tabs = new TabPane(
                tab("Результат", scroll),
                tab("Дерево", treeView),
                tab("Построение дерева", parseLogArea),
                tab("Обход дерева", walkLogArea));
        tabs.setPrefHeight(300);

        VBox root = new VBox(8, exprLabel, exprField, textLabel, textArea,
                checkBtn, statusLabel, tabs);
        root.setPadding(new Insets(14));

        stage.setTitle("Помощник проверки регулярных выражений");
        stage.setScene(new Scene(root, 720, 700));
        stage.show();
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static TextArea logArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setFont(Font.font("Monospaced", 13));
        area.setPromptText("Появится после нажатия «Проверить»");
        return area;
    }

    private void check() {
        String expr = exprField.getText();
        String text = textArea.getText();
        showFlow(new TextFlow());
        treeView.setRoot(null);
        parseLogArea.clear();
        walkLogArea.clear();

        if (expr == null || expr.isEmpty()) {
            warn("Введена пустая строка: выражение не задано.");
            return;
        }

        TraceLog parseLog = TraceLog.create();
        Regex regex;
        try {
            regex = Regex.compile(expr, parseLog);
        } catch (RegexSyntaxException ex) {
            parseLogArea.setText(String.join("\n", parseLog.lines()));
            warn("Введённое выражение не является регулярным: " + ex.getMessage()
                    + " (позиция " + (ex.getPosition() + 1) + ").");
            return;
        }
        parseLogArea.setText(String.join("\n", parseLog.lines()));
        treeView.setRoot(buildTree(regex.getRoot()));

        if (text == null || text.isEmpty()) {
            warn("Введена пустая строка: текст для поиска не задан.");
            return;
        }

        List<Match> matches = regex.findAll(text);
        showWalk(regex, text, matches);

        if (matches.isEmpty()) {
            setStatus("Выражение корректно, но в строке оно отсутствует.", Color.DARKORANGE);
            showFlow(new TextFlow(plain(text)));
            return;
        }

        TextFlow flow = new TextFlow();
        int last = 0;
        for (Match m : matches) {
            if (m.getStart() > last) {
                flow.getChildren().add(plain(text.substring(last, m.getStart())));
            }
            flow.getChildren().add(highlighted(text.substring(m.getStart(), m.getEnd())));
            last = m.getEnd();
        }
        if (last < text.length()) {
            flow.getChildren().add(plain(text.substring(last)));
        }
        showFlow(flow);
        setStatus("Выражение корректно. Найдено вхождений: " + matches.size() + ".", Color.GREEN);
    }

    private TreeItem<String> buildTree(Node node) {
        TreeItem<String> item = new TreeItem<>(node.label());
        item.setExpanded(true);
        for (Node child : node.children()) {
            item.getChildren().add(buildTree(child));
        }
        return item;
    }

    private void showWalk(Regex regex, String text, List<Match> matches) {
        int start = matches.isEmpty() ? 0 : matches.get(0).getStart();
        TraceLog walkLog = TraceLog.create();
        regex.traceAt(text, start, walkLog);
        String header = matches.isEmpty()
                ? "Вхождений нет. Показан обход с позиции 0.\n"
                : "Обход с позиции " + start + " (начало первого вхождения).\n";
        walkLogArea.setText(header + "Каждый узел получает множество позиций «откуда» и возвращает «докуда».\n\n"
                + String.join("\n", walkLog.lines()));
    }

    private void showFlow(TextFlow flow) {
        flow.setPadding(new Insets(8));
        scroll.setContent(flow);
        scroll.setVvalue(0);
        scroll.requestLayout();
    }

    private Text plain(String s) {
        Text t = new Text(s);
        t.setFont(Font.font("Monospaced", 14));
        return t;
    }

    private Text highlighted(String s) {
        Text t = new Text(s);
        t.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        t.setFill(Color.CRIMSON);
        t.setUnderline(true);
        return t;
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setTextFill(color);
        statusLabel.setText(msg);
    }

    private void warn(String msg) {
        setStatus(msg, Color.FIREBRICK);
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
