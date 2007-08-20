/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 7, 2007
 * Time: 2:44:37 PM
 */
package com.intellij.xml.refactoring;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlTagRenameHandler implements RenameHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.refactoring.XmlTagRenameHandler");


  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    //noinspection ConstantConditions
    return StandardPatterns.psiElement().withParent(XmlTag.class).accepts(element) &&
           isDeclarationOutOfProjectOrAbsent(element.getProject(), dataContext);
  }

  public boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  private static boolean isInplaceRenameAvailable(final Editor editor) {
    return editor.getSettings().isVariableInplaceRenameEnabled();
  }

  private static boolean isDeclarationOutOfProjectOrAbsent(final Project project, final DataContext context) {
    final PsiElement[] elements = BaseRefactoringAction.getPsiElementArray(context);
    LOG.assertTrue(project != null);
    return elements.length == 0 || elements.length == 1 && !PsiManager.getInstance(project).isInProject(elements[0]);
  }

  @Nullable
  private static Editor getEditor(@Nullable DataContext context) {
    return DataKeys.EDITOR.getData(context);
  }

  @Nullable
  private static PsiElement getElement(@Nullable final DataContext context) {
    if (context != null) {
      final Editor editor = getEditor(context);
      if (editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiFile file = DataKeys.PSI_FILE.getData(context);
        if (file instanceof XmlFile) {
          return file.getViewProvider().findElementAt(offset);
        }
      }
    }

    return null;
  }

  private void invoke(@Nullable final Editor editor, @NotNull final PsiElement element, @Nullable final DataContext context) {
    if (!isRenaming(context)) {
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.rename");

    if (isInplaceRenameAvailable(editor)) {
      XmlTagInplaceRenamer.rename(editor, (XmlTag)element.getParent());
    }
    else {
      XmlTagRenameDialog.renameXmlTag(editor, element, (XmlTag)element.getParent());
    }
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, @Nullable final DataContext dataContext) {
    if (!isRenaming(dataContext)) {
      return;
    }

    final PsiElement element = getElement(dataContext);
    assert element != null;

    invoke(editor, element, dataContext);
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, @Nullable final DataContext dataContext) {
    PsiElement element = (elements.length == 1) ? elements[0] : null;
    if (element == null) {
      element = getElement(dataContext);
    }

    LOG.assertTrue(element != null);
    invoke(getEditor(dataContext), element, dataContext);
  }
}