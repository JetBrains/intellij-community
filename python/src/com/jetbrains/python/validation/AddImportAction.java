/*
 * User: anna
 * Date: 07-Mar-2008
 */
package com.jetbrains.python.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
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

public class AddImportAction implements IntentionAction {
  private final PsiReference myReference;

  public AddImportAction(final PsiReference reference) {
    myReference = reference;
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
    final String referenceName = ((PyReferenceExpression)myReference).getReferencedName();
    final PsiFile[] files = FilenameIndex.getFilesByName(project, referenceName + ".py", GlobalSearchScope.allScope(project));
    return files != null && files.length > 0;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final String referenceName = ((PyReferenceExpression)myReference).getReferencedName();
    final PsiFile[] files = FilenameIndex.getFilesByName(project, referenceName + ".py", GlobalSearchScope.allScope(project));
    if (files.length == 1) {
      String text = "\n";
      LeafElement ws = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length(), null, file.getManager());
      final ASTNode importNodeToInsert = PythonLanguage.getInstance().getElementGenerator()
          .createImportStatementFromText(project, "import " + referenceName).getNode();
      file.getNode().addChild(importNodeToInsert, getFirstNonComment(file).getNode());
      file.getNode().addChild(ws, importNodeToInsert);
    }
  }

  private static PsiElement getFirstNonComment(final PsiFile file) {
    return PsiTreeUtil.skipSiblingsForward(file.getFirstChild(), PsiComment.class);
  }

  public boolean startInWriteAction() {
    return true;
  }
}