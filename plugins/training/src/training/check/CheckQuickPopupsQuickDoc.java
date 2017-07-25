package training.check;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.QuickDocUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckQuickPopupsQuickDoc implements Check{

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
        final DocumentationComponent activeDocComponent = QuickDocUtil.getActiveDocComponent(project);
        return (activeDocComponent == null || !activeDocComponent.isShowing());
    }

    @Override
    public boolean listenAllKeys() {
        return true;
    }

}
