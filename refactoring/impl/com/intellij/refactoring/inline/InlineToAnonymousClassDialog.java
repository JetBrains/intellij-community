package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCall;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;

/**
 * @author yole
 */
public class InlineToAnonymousClassDialog extends InlineOptionsDialog {
  private PsiClass myClass;
  private final PsiCall myCallToInline;

  protected InlineToAnonymousClassDialog(Project project, PsiClass psiClass, final PsiCall callToInline) {
    super(project, true, psiClass);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInvokedOnReference = (myCallToInline != null);
    setTitle(RefactoringBundle.message("inline.to.anonymous.refactoring"));
    init();
  }

  protected String getNameLabelText() {
    String className = PsiFormatUtil.formatClass(myClass, PsiFormatUtil.SHOW_NAME);
    return RefactoringBundle.message("inline.to.anonymous.name.label", className);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.to.anonymous.border.title");
  }

  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.the.class");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.class");
  }

  protected boolean isInlineThis() {
    return false;
  }

  protected void doAction() {
    invokeRefactoring(new InlineToAnonymousClassProcessor(getProject(), myClass, myCallToInline, isInlineThisOnly()));
  }
}