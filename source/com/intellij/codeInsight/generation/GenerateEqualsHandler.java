package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;

/**
 * @author dsl
 */
public class GenerateEqualsHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateEqualsHandler");
  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;
  private static final PsiField[] DUMMY_RESULT = PsiField.EMPTY_ARRAY;

  public GenerateEqualsHandler() {
    super("");
  }

  protected Object[] chooseOriginalMembers(PsiClass aClass, Project project) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;


    GlobalSearchScope scope = aClass.getResolveScope();
    final PsiMethod equalsMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getEqualsSignature(project, scope));
    final PsiMethod hashCodeMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getHashCodeSignature());

    boolean needEquals = equalsMethod == null;
    boolean needHashCode = hashCodeMethod == null;
    if (!needEquals && !needHashCode) {
      final String classText =
              aClass instanceof PsiAnonymousClass ? "this anonymous class" :
              "class " + aClass.getQualifiedName() ;
      String text = "Methods 'boolean equals(Object)' and 'int hashCode()' are already defined\n" +
                    "for " + classText + ". Do you want to delete them and proceed?";
      if (Messages.showYesNoDialog(project, text, "Generate equals() and hashCode()", Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        if (!ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
            public Boolean compute() {
              try {
                equalsMethod.delete();
                hashCodeMethod.delete();
                return Boolean.TRUE;
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
                return Boolean.FALSE;
              }
            }
          }).booleanValue()) {
          return null;
        } else {
          needEquals = needHashCode = true;
        }
      } else {
        return null;
      }
    }

    GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
    wizard.show();
    if (!wizard.isOK()) return null;
    myEqualsFields = wizard.getEqualsFields();
    myHashCodeFields = wizard.getHashCodeFields();
    myNonNullFields = wizard.getNonNullFields();
    return DUMMY_RESULT;
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object[] originalMembers) throws IncorrectOperationException {
    try {
      Project project = aClass.getProject();
      GenerateEqualsHelper helper = new GenerateEqualsHelper(project, aClass, myEqualsFields, myHashCodeFields, myNonNullFields);
      return helper.generateMembers();
    }
    catch (GenerateEqualsHelper.NoObjectClassException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog("Cannot generate equals() and hashCode().\nNo java.lang.Object class found.",
                                     "No java.lang.Object");
          }
        });
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  protected Object[] getAllOriginalMembers(PsiClass aClass) {
    return null;
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object originalMember) {
    return null;
  }

}
