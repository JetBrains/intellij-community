package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.util.IncorrectOperationException;

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
    final PsiClass aClass,
    final String fieldClass,
    final String fieldName
  ) {
    super(editor, "Create Field '" + fieldName + "'");
    if (aClass == null) {
      throw new IllegalArgumentException("aClass cannot be null");
    }
    if (fieldClass == null) {
      throw new IllegalArgumentException("fieldClass cannot be null");
    }
    if (fieldName == null) {
      throw new IllegalArgumentException("fieldName cannot be null");
    }
    myClass = aClass;
    myFieldClassName = fieldClass;
    myFieldName = fieldName;
  }

  /**
   * @param showErrors if <code>true</code> the error messages will be shown to the
   * user. Otherwise method works silently.
   */
  public static void runImpl(
    final GuiEditor editor,
    final PsiClass boundClass,
    final String fieldClassName,
    final String fieldName,
    final boolean showErrors
  ){
    LOG.assertTrue(editor != null);
    LOG.assertTrue(boundClass != null);
    LOG.assertTrue(fieldClassName != null);
    LOG.assertTrue(fieldName != null);
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = editor.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    // Do nothing if file becomes invalid
    if(!boundClass.isValid()){
      return;
    }

    if(!boundClass.isWritable()){
      if(showErrors) {
        if (!RefactoringMessageUtil.checkReadOnlyStatus(boundClass, project,
                                                        "Cannot create field '" + fieldClassName + "'")) {
          return;
        }
      } else return;
    }

    final PsiClass fieldClass = PsiManager.getInstance(project).findClass(
      fieldClassName,
      GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(editor.getModule())
    );
    if(fieldClass == null){
      if(showErrors){
        Messages.showErrorDialog(
          editor,
          "Cannot create field '" + fieldName + "' because\nclass '" + fieldClassName + "' does not exist.",
          "Error"
        );
      }
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(
            editor.getProject(),
            new Runnable() {
              public void run() {
                createField(project, fieldClass, fieldName, boundClass, showErrors, editor);
              }
            },
            "Create Field",
            null
          );
        }
      }
    );
  }

  private static void createField(final Project project,
                                  final PsiClass fieldClass,
                                  final String fieldName,
                                  final PsiClass boundClass,
                                  final boolean showErrors, final GuiEditor editor) {// 1. Create field
    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
    final PsiType type = factory.createType(fieldClass);
    LOG.assertTrue(type != null);
    try {
      final PsiField field = factory.createField(fieldName, type);
      boundClass.add(field);
    }
    catch (final IncorrectOperationException exc) {
      if (showErrors) {
        ApplicationManager.getApplication().invokeLater(
          new Runnable() {
            public void run() {
              Messages.showErrorDialog(
                editor,
                "Cannot create field '" + fieldName + "'.\nReason: " + exc.getMessage(),
                "Error"
              );
            }
          }
        );
      }
      return;
    }
  }

  public void run() {
    runImpl(myEditor, myClass, myFieldClassName, myFieldName, true);
  }
}
