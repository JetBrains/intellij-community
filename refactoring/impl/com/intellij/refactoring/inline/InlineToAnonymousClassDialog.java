package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * @author yole
 */
public class InlineToAnonymousClassDialog extends InlineOptionsDialog {
  private PsiClass myClass;

  protected InlineToAnonymousClassDialog(Project project, PsiClass psiClass) {
    super(project, true, psiClass);
    myClass = psiClass;
    setTitle("Inline to Anonymous Class");
    init();
  }

  protected String getNameLabelText() {
    String className = PsiFormatUtil.formatClass(myClass, PsiFormatUtil.SHOW_NAME);
    return "Class " + className;
  }

  protected String getBorderTitle() {
    return "Inline";
  }

  protected String getInlineAllText() {
    return "All references and remove the class";
  }

  protected String getInlineThisText() {
    return "This reference only and keep the class";
  }

  protected boolean isInlineThis() {
    return false;
  }

  protected void doAction() {
    invokeRefactoring(new InlineToAnonymousClassProcessor(getProject(), myClass, isInlineThisOnly()));
  }
}