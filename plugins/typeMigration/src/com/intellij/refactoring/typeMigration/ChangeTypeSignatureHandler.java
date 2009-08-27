package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class ChangeTypeSignatureHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ChangeTypeSignatureHandler.class.getName());

  public static final String REFACTORING_NAME = "Type Migration";

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement element = file.findElementAt(offset);
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
    while (typeElement != null) {
      final PsiElement parent = typeElement.getParent();
      if (parent instanceof PsiVariable || parent instanceof PsiMember || (parent instanceof PsiReferenceParameterList && PsiTreeUtil.getParentOfType(parent, PsiMember.class) instanceof PsiClass)) {
        invoke(project, parent, null, editor);
        return;
      }
      typeElement = PsiTreeUtil.getParentOfType(parent, PsiTypeElement.class, false);
    }
    CommonRefactoringUtil.showErrorHint(project, editor,
                                        "The caret should be positioned on type of field, variable, method or method parameter to be refactored",
                                        REFACTORING_NAME, "refactoring.migrateType");
  }


  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    final PsiElement element = elements[0];
    invokeOnElement(project, element);
  }

  public static boolean invokeOnElement(final Project project, final PsiElement element) {
    if (element instanceof PsiVariable || element instanceof PsiMember || element instanceof PsiFile) {
      invoke(project, element, null, null);
      return true;
    }
    if (element instanceof PsiReferenceParameterList && PsiTreeUtil.getParentOfType(element, PsiMember.class) instanceof PsiClass) {
      invoke(project, element, null, null);
      return true;
    }
    return false;
  }

  public static void invoke(final Project project, final PsiElement root, final TypeMigrationRules rules, final Editor editor) {
    if (Util.canBeMigrated(root)) {
      TypeMigrationDialog dialog = new TypeMigrationDialog(project, root, rules);
      dialog.show();
      return;
    }

    CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("only.fields.variables.of.methods.of.valid.type.can.be.considered"),
                                   RefactoringBundle.message("unable.to.start.type.migration"), null);

  }
}