package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;

public class InlineMethodDialog extends InlineOptionsDialog {
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");
  private PsiJavaCodeReferenceElement myReferenceElement;
  private final Editor myEditor;
  private final boolean myAllowInlineThisOnly;

  private final PsiMethod myMethod;

  public InlineMethodDialog(Project project, PsiMethod method, PsiJavaCodeReferenceElement ref, Editor editor,
                            final boolean allowInlineThisOnly) {
    super(project, true, method);
    myMethod = method;
    myReferenceElement = ref;
    myEditor = editor;
    myAllowInlineThisOnly = allowInlineThisOnly;
    myInvokedOnReference = ref != null;

    setTitle(REFACTORING_NAME);

    init();
  }

  protected String getNameLabelText() {
    String methodText = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
    return RefactoringBundle.message("inline.method.method.label", methodText);
  }

  protected String getBorderTitle() {
    return RefactoringBundle.message("inline.method.border.title");
  }

  protected String getInlineThisText() {
    return RefactoringBundle.message("this.invocation.only.and.keep.the.method");
  }

  protected String getInlineAllText() {
    return myMethod.isWritable()
           ? RefactoringBundle.message("all.invocations.and.remove.the.method")
           : RefactoringBundle.message("all.invocations.in.project");
  }

  protected void doAction() {
    invokeRefactoring(new InlineMethodProcessor(getProject(), myMethod, myReferenceElement, myEditor, isInlineThisOnly()));
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_METHOD_THIS = isInlineThisOnly();
    }
  }

  protected void doHelpAction() {
    if (myMethod.isConstructor()) HelpManager.getInstance().invokeHelp(HelpID.INLINE_CONSTRUCTOR);
    else HelpManager.getInstance().invokeHelp(HelpID.INLINE_METHOD);
  }

  protected boolean canInlineThisOnly() {
    return InlineMethodHandler.checkRecursive(myMethod) || myAllowInlineThisOnly;
  }

  protected boolean isInlineThis() {
    return RefactoringSettings.getInstance().INLINE_METHOD_THIS;
  }
}
