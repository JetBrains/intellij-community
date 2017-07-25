package training.check;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckCountCommentedLine implements Check{

    Project project;
    Editor editor;
    int countComments;

    @Override
    public void set(Project project, Editor editor) {
        this.project = project;
        this.editor = editor;
    }

    @Override
    public void before() {
        countComments = countCommentedLines();
    }

    @Override
    public boolean check() {
        return (countCommentedLines() == 0);
    }

    @Override
    public boolean listenAllKeys() {
        return false;
    }

    public int countCommentedLines(){

        final PsiElement psiElement = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        ASTNode astNode = psiElement.getNode();
        while (astNode.getTreeParent() != null) {
            astNode = astNode.getTreeParent();
        }
        return calc(astNode.getPsi());
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
