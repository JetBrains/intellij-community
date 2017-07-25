package training.check;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

/**
 * Created by karashevich on 24/11/15.
 */
public class CheckFindBar implements Check{


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
        return (editor.getHeaderComponent() == null);
    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }
}
