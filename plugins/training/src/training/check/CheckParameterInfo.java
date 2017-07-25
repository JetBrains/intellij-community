package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckParameterInfo implements Check{

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

        final Collection<PsiMethodCallExpression> childrenOfType = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethodCallExpression.class);
        PsiMethodCallExpression myMethodCall = null;
        for(PsiMethodCallExpression methodCall: childrenOfType){
            methodCall.getMethodExpression().getCanonicalText().equals("frame.getSize");
            myMethodCall = methodCall;
            break;
        }

        if (myMethodCall == null) return false;
        final Collection<PsiLiteralExpression> literals = PsiTreeUtil.findChildrenOfType(myMethodCall, PsiLiteralExpression.class);
        if (literals.size() != 2) return false;
        else {
            if (((PsiLiteralExpression)literals.toArray()[0]).getValue() instanceof Integer && ((PsiLiteralExpression)literals.toArray()[1]).getValue() instanceof Integer) {
                final Integer width = (Integer)((PsiLiteralExpression) literals.toArray()[0]).getValue();
                final Integer height = (Integer)((PsiLiteralExpression) literals.toArray()[1]).getValue();
                if (width != null && height != null && width == 175 && height == 100) return true;
            }
        }
        return false;

    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

}
