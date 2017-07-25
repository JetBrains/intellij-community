package training.check;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckSelectedLines implements Check{

    Project project;
    Editor editor;

    @Override
    public void set(Project project, Editor editor) {
        this.project = project;
        this.editor = editor;
    }

    @Override
    public void before() {
    }

    @Override
    public boolean check() {
        return calc() >= 2;
    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }


    private int calc(){

        final int lineStart = editor.getSelectionModel().getSelectionStartPosition().getLine();
        final int lineEnd = editor.getSelectionModel().getSelectionEndPosition().getLine();

        return lineEnd - lineStart;
    }
}
