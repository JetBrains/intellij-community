package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.CommonBundle;
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
   * @param showErrors if <code>true</code> the error messages will be shown to the
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

    final PsiClass fieldClass = PsiManager.getInstance(project).findClass(
      fieldClassName,
      GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(rootContainer.getModule())
    );
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
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                createField(project, fieldClass, fieldName, boundClass, showErrors, rootContainer);
              }
            }
          );
        }
      },
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
    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
    final PsiType type = factory.createType(fieldClass);
    try {
      final PsiField field = factory.createField(fieldName, type);
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
          new Runnable() {
            public void run() {
              Messages.showErrorDialog(
                project,
                UIDesignerBundle.message("error.cannot.create.field.reason", fieldName, exc.getMessage()),
                CommonBundle.getErrorTitle()
              );
            }
          }
        );
      }
    }
  }

  public void run() {
    runImpl(myEditor.getProject(), myEditor.getRootContainer(), myClass, myFieldClassName, myFieldName, true, null);
  }
}
