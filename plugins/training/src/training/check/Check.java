package training.check;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

/**
 * Created by karashevich on 21/08/15.
 */
public interface Check {

    void set(Project project, Editor editor);

    void before();

    boolean check();

    boolean listenAllKeys();

}
