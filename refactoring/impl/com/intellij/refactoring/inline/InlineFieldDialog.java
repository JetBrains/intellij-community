package com.intellij.refactoring.inline;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;

public class InlineFieldDialog extends InlineOptionsDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");
  private PsiReferenceExpression myReferenceExpression;

  private final PsiField myField;

  public InlineFieldDialog(Project project, PsiField field, PsiReferenceExpression ref) {
    super(project, true, field);
    myField = field;
    myReferenceExpression = ref;
    myInvokedOnReference = myReferenceExpression != null;

    setTitle(REFACTORING_NAME);

    init();
  }

  protected String getNameLabelText() {
    String fieldText = PsiFormatUtil.formatVariable(myField,
                                                    PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE ,PsiSubstitutor.EMPTY);
    return RefactoringBundle.message("inline.field.field.name.label", fieldText);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.field.border.title");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.reference.only.and.keep.the.field");
  }

  protected String getInlineAllText() {
    return RefactoringBundle.message("all.references.and.remove.the.field");
  }

  protected boolean isInlineThis() {
    return RefactoringSettings.getInstance().INLINE_FIELD_THIS;
  }

  protected void doAction() {
    invokeRefactoring(new InlineConstantFieldProcessor(myField, getProject(), myReferenceExpression, isInlineThisOnly()));
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_FIELD_THIS = isInlineThisOnly();
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_FIELD);
  }
}
