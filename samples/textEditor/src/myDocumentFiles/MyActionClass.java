package myDocumentFiles;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Aug 30, 2010
 * Time: 6:29:02 PM
 */
public class MyActionClass extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        MyVisualPanel myEditor = new MyVisualPanel(false);
        myEditor.getPeer().setTitle("Sample Text File Editor");
        myEditor.show();
    }
}
