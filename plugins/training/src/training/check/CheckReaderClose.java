package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckReaderClose implements Check{

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

        PsiKeyword finallyKeyword = null;
        final Collection<PsiKeyword> childrenOfType = PsiTreeUtil.findChildrenOfType((PsiElement) psiFile, PsiKeyword.class);
        for (PsiKeyword psiKeyword : childrenOfType) {
            if(psiKeyword.getText().equals("finally")) finallyKeyword = psiKeyword;
        }
        if (finallyKeyword == null) return false;

        final PsiElement nextSibling = finallyKeyword.getNextSibling().getNextSibling();
        if ((nextSibling instanceof PsiCodeBlock) && (nextSibling).getText().contains("fileReader.close();"))
            return true;

//        final PsiJavaCodeReferenceElement javaCodeReferenceElement = PsiTreeUtil.findChildOfType(referenceList, PsiJavaCodeReferenceElement.class);

//        if (javaCodeReferenceElement.getText().equals("IOException")) return true;
        return false;

    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

}
