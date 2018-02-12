/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateFieldFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.quickFixes.CreateFieldFix");

  private final PsiClass myClass;
  private final String myFieldClassName;
  private final String myFieldName;

  public CreateFieldFix(
    final GuiEditor editor,
    @NotNull final PsiClass aClass,
    @NotNull final String fieldClass,
    @NotNull final String fieldName
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
  public static void runImpl(@NotNull final Project project,
                             @NotNull final RadContainer rootContainer,
                             @NotNull final PsiClass boundClass,
                             @NotNull final String fieldClassName,
                             @NotNull final String fieldName,
                             final boolean showErrors,
                             @Nullable final Object undoGroupId) {
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

  public void run() {
    runImpl(myEditor.getProject(), myEditor.getRootContainer(), myClass, myFieldClassName, myFieldName, true, null);
  }
}
