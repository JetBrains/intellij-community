package training.check;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Created by karashevich on 22/09/15.
 */
public class CheckJumpFromString implements Check {

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
        final CaretModel caretModel = editor.getCaretModel();
        final Document document = editor.getDocument();
        final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        assert psiFile != null;
        final PsiElement elementAt = psiFile.findElementAt(caretModel.getOffset());
        assert elementAt != null;
        return (elementAt.getParent() != null) && (elementAt.getParent().getText().equals("String"));

    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

    private int calc(PsiElement psiElement){

        if (psiElement.getNode().getElementType() == JavaTokenType.END_OF_LINE_COMMENT) return 1;
        else if(psiElement.getChildren().length == 0) return 0;
        else {
            int result = 0;
            for (PsiElement psiChild : psiElement.getChildren()) {
                result += calc(psiChild);
            }
            return result;
        }
    }
}
