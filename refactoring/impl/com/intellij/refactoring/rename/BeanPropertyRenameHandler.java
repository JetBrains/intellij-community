/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class BeanPropertyRenameHandler implements RenameHandler {

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return false;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final BeanProperty property = getProperty(dataContext);
    new PropertyRenameDialog(property).show();
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {

  }

  @Nullable
  protected abstract BeanProperty getProperty(DataContext context);

  private static class PropertyRenameDialog extends RenameDialog {

    private final BeanProperty myProperty;

    protected PropertyRenameDialog(BeanProperty property) {
      super(property.getMethod().getProject(), property.getPsiElement(), null, null);
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final RenameRefactoring rename = RefactoringFactory.getInstance(getProject()).createRename(myProperty.getPsiElement(), newName);

      final PsiMethod setter = myProperty.getSetter();
      if (setter != null) {
        final String setterName = PropertyUtil.suggestSetterName(newName);
        rename.addElement(setter, setterName);
      }

      final PsiMethod getter = myProperty.getGetter();
      if (getter != null) {
        final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
        rename.addElement(getter, getterName);
      }

      rename.run();
      close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
