package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.pytest.PyTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.test");
  }


  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PyClass psiClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    if (psiClass != null && PyTestUtil.isPyTestClass(psiClass))
      return false;
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    final PyFunction srcFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    final PyClass srcClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    if (srcClass == null && srcFunction == null) return;

    final CreateTestDialog d = new CreateTestDialog(project);
    if (srcClass != null) {
      d.setClassName("Test"+StringUtil.capitalize(srcClass.getName()));
      d.setFileName("test_"+StringUtil.decapitalize(srcClass.getName()) + ".py");
      PsiDirectory dir = file.getContainingDirectory();
      if (dir != null)
        d.setTargetDir(dir.getVirtualFile().getPath());

      if (srcFunction != null) {
        d.methodsSize(1);
        d.addMethod("test_"+srcFunction.getName(), 0);
      }
      else {
        d.methodsSize(srcClass.getMethods().length);
        int i = 0;
        for (PyFunction f : srcClass.getMethods()) {
          if (f.getName() != null && !f.getName().startsWith("__")) {
            d.addMethod("test_"+f.getName(), i);
            ++i;
          }
        }
      }
    }
    else {
      d.setClassName("Test"+ StringUtil.capitalize(srcFunction.getName()));
      d.setFileName("test_"+StringUtil.decapitalize(srcFunction.getName())+ ".py");
      PsiDirectory dir = file.getContainingDirectory();
      if (dir != null)
        d.setTargetDir(dir.getVirtualFile().getPath());

      d.methodsSize(1);
      d.addMethod("test_"+srcFunction.getName(), 0);
    }

    d.show();
    if (!d.isOK()) return;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        final PyTestGenerator generator = new PyTestGenerator();
        PsiFile e = (PsiFile)generator.generateTest(project, d);
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        documentManager.commitAllDocuments();


      }
    }, CodeInsightBundle.message("intention.create.test"), this);
  }
}