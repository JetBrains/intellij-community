package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class AddMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddMethodFix");

  private PsiClass myClass;
  private PsiMethod myMethod;
  private String myText;
  private List<String> myExceptions = new ArrayList<String>();

  public AddMethodFix(PsiMethod method, PsiClass implClass) {
    init(method, implClass);
  }

  public AddMethodFix(String methodText, PsiClass implClass) {
    try {
      init(implClass.getManager().getElementFactory().createMethodFromText(methodText, implClass), implClass);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void init(PsiMethod method, PsiClass implClass) {
    if (method == null || implClass == null) return;
    myMethod = method;
    myClass = implClass;
    setText("Add Method '" + method.getName() + "' to Class '" + implClass.getName() + "'");
  }

  private static PsiMethod reformat(Project project, PsiMethod result) throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    result = (PsiMethod) codeStyleManager.shortenClassReferences(result);
    result = (PsiMethod) codeStyleManager.reformat(result);
    return result;
  }

  public void setText(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public String getFamilyName() {
    return "Add Method";
  }

  public void addException(String exception) {
    myExceptions.add(exception);
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myText != null
        && MethodSignatureUtil.findMethodBySignature(myClass, myMethod, false) == null
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    try {
      if (myClass.isInterface() && myMethod.getBody() != null) myMethod.getBody().delete();
      myMethod = (PsiMethod) myClass.add(myMethod);
      for (String exception : myExceptions) {
        PsiUtil.addException(myMethod, exception);
      }
      myMethod = (PsiMethod)myMethod.replace(reformat(project, myMethod));
      GenerateMembersUtil.positionCaret(editor, myMethod, true);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
