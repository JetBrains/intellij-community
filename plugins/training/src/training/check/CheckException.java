package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckException implements Check{

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

        final PsiMethod mainMethod = PsiTreeUtil.findChildOfType((PsiElement) psiFile, PsiMethod.class);
        final PsiReferenceList referenceList = PsiTreeUtil.findChildOfType((PsiElement) mainMethod, PsiReferenceList.class);
        final PsiJavaCodeReferenceElement javaCodeReferenceElement = PsiTreeUtil.findChildOfType(referenceList, PsiJavaCodeReferenceElement.class);
        if (javaCodeReferenceElement.getText().equals("IOException")) return true;
        return false;
    }

    @Override
    public boolean listenAllKeys() {
        return true;
    }

}
