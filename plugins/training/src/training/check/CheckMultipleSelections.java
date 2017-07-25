package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckMultipleSelections implements Check{

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

        final Document document = editor.getDocument();
        final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

        final Collection<HtmlTag> childrenOfType1 = PsiTreeUtil.findChildrenOfType(psiFile, HtmlTag.class);

        int count = 0;

        for (HtmlTag htmlTag: childrenOfType1){
            if (htmlTag.getName().equals("th")) return false;
            if (htmlTag.getName().equals("td")) count++;
        }

        return count == 6;

    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

}
