// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CreateFieldFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance(CreateFieldFix.class);

  private final PsiClass myClass;
  private final String myFieldClassName;
  private final String myFieldName;

  public CreateFieldFix(
    final GuiEditor editor,
    final @NotNull PsiClass aClass,
    final @NotNull String fieldClass,
    final @NotNull String fieldName
  ) {
    super(editor, UIDesignerBundle.message("action.create.field", fieldName), null);
    myClass = aClass;
    myFieldClassName = fieldClass;
    myFieldName = fieldName;
  }

  /**
   * @param showErrors if {@code true} the error messages will be shown to the
   * @param undoGroupId the group used to undo the action together with some other action.
   */
  public static void runImpl(final @NotNull Project project,
                             final @NotNull RadContainer rootContainer,
                             final @NotNull PsiClass boundClass,
                             final @NotNull String fieldClassName,
                             final @NotNull String fieldName,
                             final boolean showErrors,
                             final @Nullable Object undoGroupId) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    // Do nothing if file becomes invalid
    if(!boundClass.isValid()){
      return;
    }

    if(!boundClass.isWritable()){
      if(showErrors) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(boundClass, project,
                                                       UIDesignerBundle.message("error.cannot.create.field", fieldClassName))) {
          return;
        }
      } else return;
    }

    final PsiClass fieldClass = JavaPsiFacade.getInstance(project)
      .findClass(fieldClassName.replace('$', '.'), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(rootContainer.getModule()));
    if(fieldClass == null){
      if(showErrors){
        Messages.showErrorDialog(
          project,
          UIDesignerBundle.message("error.cannot.create.field.no.class", fieldName, fieldClassName),
          CommonBundle.getErrorTitle()
        );
      }
      return;
    }

    CommandProcessor.getInstance().executeCommand(
      project,
      () -> ApplicationManager.getApplication().runWriteAction(
        () -> createField(project, fieldClass, fieldName, boundClass, showErrors, rootContainer)
      ),
      UIDesignerBundle.message("command.create.field"),
      undoGroupId
    );
  }

  private static void createField(final Project project,
                                  final PsiClass fieldClass,
                                  final String fieldName,
                                  final PsiClass boundClass,
                                  final boolean showErrors,
                                  final IContainer rootContainer) {
    // 1. Create field
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiType type = factory.createType(fieldClass);
    try {
      final PsiField field = factory.createField(fieldName, type);
      final String accessibility = GuiDesignerConfiguration.getInstance(project).DEFAULT_FIELD_ACCESSIBILITY;
      final PsiModifierList modifierList = field.getModifierList();
      assert modifierList != null;
      String[] modifiers = {PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
      for(@PsiModifier.ModifierConstant String modifier: modifiers) {
        modifierList.setModifierProperty(modifier, accessibility.equals(modifier));
      }
      PsiField lastUiField = null;
      for(PsiField uiField: boundClass.getFields()) {
        if (FormEditingUtil.findComponentWithBinding(rootContainer, uiField.getName()) != null) {
          lastUiField = uiField;
        }
      }
      if (lastUiField != null) {
        boundClass.addAfter(field, lastUiField);
      }
      else {
        boundClass.add(field);
      }
    }
    catch (final IncorrectOperationException exc) {
      if (showErrors) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showErrorDialog(
            project,
            UIDesignerBundle.message("error.cannot.create.field.reason", fieldName, exc.getMessage()),
            CommonBundle.getErrorTitle()
          )
        );
      }
    }
  }

  @Override
  public void run() {
    runImpl(myEditor.getProject(), myEditor.getRootContainer(), myClass, myFieldClassName, myFieldName, true, null);
  }
}
