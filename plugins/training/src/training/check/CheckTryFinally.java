package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckTryFinally implements Check{

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

        boolean tryText = false;
        boolean finallyText = false;

        final Collection<PsiKeyword> childrenOfType = PsiTreeUtil.findChildrenOfType((PsiElement) psiFile, PsiKeyword.class);
        for (PsiKeyword aChildrenOfType : childrenOfType) {
            if (aChildrenOfType.getText().equals("try")) tryText = true;
            if (aChildrenOfType.getText().equals("finally")) finallyText = true;
        }

        return (tryText && finallyText);
    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

}
