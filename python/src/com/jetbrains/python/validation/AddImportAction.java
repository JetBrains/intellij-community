/*
 * User: anna
 * Date: 07-Mar-2008
 */
package com.jetbrains.python.validation;

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

public class AddImportAction implements HintAction, QuestionAction {
  private final PsiReference myReference;
  private Project myProject;

  public AddImportAction(final PsiReference reference) {
    myReference = reference;
    myProject = reference.getElement().getProject();
  }

  @NotNull
  public String getText() {
    return "Add import";
  }

  @NotNull
  public String getFamilyName() {
    return "import";
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return isAvailable();
  }

  private boolean isAvailable() {
    final String referenceName = ((PyReferenceExpression)myReference).getReferencedName();
    final PsiFile[] files = FilenameIndex.getFilesByName(myProject, referenceName + ".py", GlobalSearchScope.allScope(myProject));
    return files != null && files.length > 0;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    execute(file);
  }

  private void execute(final PsiFile file) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final String referenceName = ((PyReferenceExpression)myReference).getReferencedName();
        final PsiFile[] files = FilenameIndex.getFilesByName(myProject, referenceName + ".py", GlobalSearchScope.allScope(myProject));
        if (files.length == 1) {
          String text = "\n";
          LeafElement ws = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length(), null, file.getManager());
          final ASTNode importNodeToInsert = PythonLanguage.getInstance().getElementGenerator()
              .createImportStatementFromText(myProject, "import " + referenceName).getNode();
          final PsiElement element = getFirstNonComment(file);
          file.getNode().addChild(importNodeToInsert, element != null ? element.getNode() : file.getFirstChild().getNode());
          file.getNode().addChild(ws, importNodeToInsert);
        }
      }
    });
  }

  private static PsiElement getFirstNonComment(final PsiFile file) {
    return PsiTreeUtil.skipSiblingsForward(file.getFirstChild(), PsiComment.class);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean showHint(final Editor editor) {
    if (!isAvailable()) return false;
    String hintText = ShowAutoImportPass.getMessage(false, ((PyReferenceExpression)myReference).getReferencedName());
    HintManager.getInstance().showQuestionHint(editor, hintText, myReference.getElement().getTextOffset(),
                                               myReference.getElement().getTextRange().getEndOffset(), this);
    return true;
  }

  public boolean execute() {
    execute(myReference.getElement().getContainingFile());
    return true;
  }
}