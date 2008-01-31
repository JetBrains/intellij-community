package com.intellij.refactoring.changeClassSignature;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ChangeClassSignatureTest extends LightCodeInsightTestCase {
  public void testNoParams() throws Exception {
    doTest(new GenParams() {
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[]{
          new TypeParameterInfo(aClass, "T", "java.lang.String")
        };
      }
    });
  }

  public void testRemoveAllParams() throws Exception {
    doTest(new GenParams() {
      public TypeParameterInfo[] gen(PsiClass aClass) {
        return new TypeParameterInfo[0];
      }
    });
  }

  public void testReorderParams() throws Exception {
    doTest(new GenParams() {
      public TypeParameterInfo[] gen(PsiClass aClass) {
        return new TypeParameterInfo[] {
          new TypeParameterInfo(1),
          new TypeParameterInfo(0)
        };
      }
    });
  }

  public void testAddParam() throws Exception {
    doTest(new GenParams() {
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[] {
          new TypeParameterInfo(0),
          new TypeParameterInfo(aClass, "E", "L<T>")
        };
      }
    });
  }

  private void doTest(GenParams gen) throws Exception {
    final String filePath = "/refactoring/changeClassSignature/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on class name", targetElement instanceof PsiClass);
    PsiClass aClass = (PsiClass)targetElement;
    new ChangeClassSignatureProcessor(getProject(), aClass, gen.gen(aClass)).run();
    checkResultByFile(filePath + ".after");
  }

  public interface GenParams {
    TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException;
  }

}
