package com.intellij.refactoring.copy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

public class CopyClassesHandler implements CopyHandlerDelegate {
  public boolean canCopy(PsiElement[] elements) {
    elements = convertToTopLevelClass(elements);
    for (PsiElement element : elements) {
      if (element instanceof JspClass || element instanceof JspHolderMethod) return false;
    }

    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass && elements[0].getParent() instanceof PsiFile && elements[0].getLanguage() == StdLanguages.JAVA) {
        return true;
      }
    }

    return false;
  }

  private static PsiElement[] convertToTopLevelClass(final PsiElement[] elements) {
    if (elements.length == 1) {
      return new PsiElement[] { getTopLevelClass(elements [0]) };
    }
    return elements;
  }

  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    elements = convertToTopLevelClass(elements);
    Project project = elements [0].getProject();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = (PsiClass)elements[0];
    if (defaultTargetDirectory == null) {
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile != null) { // ???
        defaultTargetDirectory = containingFile.getContainingDirectory();
      }
    }
    CopyClassDialog dialog = new CopyClassDialog(aClass, defaultTargetDirectory, project, false);
    dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
    dialog.show();
    if (dialog.isOK()) {
      PsiDirectory targetDirectory = dialog.getTargetDirectory();
      String className = dialog.getClassName();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.copy.class"), false);
    }
  }

  public void doClone(PsiElement element) {
    element = getTopLevelClass(element);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = (PsiClass)element;
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(aClass, null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    dialog.show();
    if (dialog.isOK()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
    }
  }

  private static void copyClassImpl(final String copyClassName, final Project project, final PsiClass aClass, final PsiDirectory targetDirectory, String commandName, final boolean selectInActivePanel) {
    if (copyClassName == null || copyClassName.length() == 0) return;
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiElement newElement = doCopyClass(aClass, copyClassName, targetDirectory);
              CopyHandler.updateSelectionInActiveProjectView(newElement, project, selectInActivePanel);
              EditorHelper.openInEditor(newElement);

              result[0] = true;
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

  public static PsiElement doCopyClass(final PsiClass aClass, final String copyClassName, final PsiDirectory targetDirectory)
      throws IncorrectOperationException {
    PsiElement elementToCopy = aClass.getNavigationElement();
    ChangeContextUtil.encodeContextInfo(elementToCopy, true);
    PsiClass classCopy = (PsiClass)elementToCopy.copy();
    ChangeContextUtil.clearContextInfo(aClass);
    classCopy.setName(copyClassName);
    final String fileName = copyClassName + "." + StdFileTypes.JAVA.getDefaultExtension();
    final PsiFile createdFile = targetDirectory.copyFileFrom(fileName, elementToCopy.getContainingFile());
    PsiElement newElement = createdFile;
    if (createdFile instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)createdFile).getClasses();
      assert classes.length > 0 : createdFile.getText();
      createdFile.deleteChildRange(classes[0], classes[classes.length - 1]);
      PsiClass newClass = (PsiClass)createdFile.add(classCopy);
      ChangeContextUtil.decodeContextInfo(newClass, newClass, null);
      replaceClassOccurrences(newClass, (PsiClass) elementToCopy);
      newElement = newClass;
    }
    return newElement;
  }

  private static void replaceClassOccurrences(final PsiClass newClass, final PsiClass oldClass) throws IncorrectOperationException {
    final List<PsiJavaCodeReferenceElement> selfReferences = new ArrayList<PsiJavaCodeReferenceElement>();
    newClass.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(final PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement target = reference.resolve();
        if (target == oldClass) {
          selfReferences.add(reference);
        }
      }
    });
    for (PsiJavaCodeReferenceElement selfReference : selfReferences) {
      selfReference.bindToElement(newClass);
    }
  }

  @Nullable
  private static PsiClass getTopLevelClass(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) break;
      element = element.getParent();
    }
    if (element instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }
    return element instanceof PsiClass ? (PsiClass)element : null;
  }
}
