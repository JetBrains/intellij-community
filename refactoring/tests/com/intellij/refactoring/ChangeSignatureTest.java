package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.idea.Bombed;

import java.util.Calendar;

/**
 * @author dsl
 */
public class ChangeSignatureTest extends CodeInsightTestCase {
  public void testSimple() throws Exception {
    doTest(null, null, null,
      new ParameterInfo[0], new ThrownExceptionInfo[0], false);
  }

  public void testParameterReorder() throws Exception {
    doTest(null,
           new ParameterInfo[]{new ParameterInfo(1), new ParameterInfo(0)}, false);
  }

  public void testGenericTypes() throws Exception {
    doTest(null, null, "T", new GenParams() {
      public ParameterInfo[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = PsiManager.getInstance(getProject()).getElementFactory();
        return new ParameterInfo[]{
          new ParameterInfo(-1, "x", factory.createTypeFromText("T", method.getParameterList()), "null"),
          new ParameterInfo(-1, "y", factory.createTypeFromText("C<T>", method.getParameterList()), "null")
        };
      }
    }, false);
  }

  public void testGenericTypesInOldParameters() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfo[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = PsiManager.getInstance(getProject()).getElementFactory();
        return new ParameterInfo[] {
          new ParameterInfo(0, "t", factory.createTypeFromText("T", method), null)
        };
      }
    }, false);
  }

  public void testTypeParametersInMethod() throws Exception {
    doTest(null, null, null, new GenParams() {
             public ParameterInfo[] genParams(PsiMethod method) throws IncorrectOperationException {
               final PsiElementFactory factory = PsiManager.getInstance(getProject()).getElementFactory();
               return new ParameterInfo[]{
                   new ParameterInfo(-1, "t", factory.createTypeFromText("T", method.getParameterList()), "null"),
                   new ParameterInfo(-1, "u", factory.createTypeFromText("U", method.getParameterList()), "null"),
                   new ParameterInfo(-1, "cu", factory.createTypeFromText("C<U>", method.getParameterList()), "null")
                 };
             }
           }, false);
  }

  public void testDefaultConstructor() throws Exception {
    doTest(null,
           new ParameterInfo[] {
              new ParameterInfo(-1, "j", PsiType.INT, "27")
           }, false);
  }

  public void testGenerateDelegate() throws Exception {
    doTest(null,
           new ParameterInfo[] {
             new ParameterInfo(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateForAbstract() throws Exception {
    doTest(null,
           new ParameterInfo[] {
             new ParameterInfo(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithReturn() throws Exception {
    doTest(null,
           new ParameterInfo[] {
             new ParameterInfo(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithParametersReordering() throws Exception {
    doTest(null,
           new ParameterInfo[] {
             new ParameterInfo(1),
             new ParameterInfo(-1, "c", PsiType.CHAR, "'a'"),
             new ParameterInfo(0, "j", PsiType.INT)
           }, true);
  }

  public void testGenerateDelegateConstructor() throws Exception {
    doTest(null, new ParameterInfo[0], true);
  }

  public void testGenerateDelegateDefaultConstructor() throws Exception {
    doTest(null, new ParameterInfo[] {
      new ParameterInfo(-1, "i", PsiType.INT, "27")
    }, true);
  }

  @Bombed(user = "lesya", day = 4, month = Calendar.MAY, description = "Need to fix javadoc formatter", year = 2006, time = 15)
  public void testSCR40895() throws Exception {
    doTest(null, new ParameterInfo[] {
      new ParameterInfo(0, "y", PsiType.INT),
      new ParameterInfo(1, "b", PsiType.BOOLEAN)
    }, false);
  }

  public void testUseAnyVariable() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfo[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = method.getManager().getElementFactory();
        return new ParameterInfo[] {
          new ParameterInfo(-1, "l", factory.createTypeFromText("List", method), "null", true)
        };
      }
    }, false);
  }

  public void testEnumConstructor() throws Exception {
    doTest(null, new ParameterInfo[] {
      new ParameterInfo(-1, "i", PsiType.INT, "10")
    }, false);
  }

  public void testVarargs1() throws Exception {
    doTest(null, new ParameterInfo[] {
      new ParameterInfo(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfo(0)
    }, false);
  }

  public void testCovariantReturnType() throws Exception {
    doTest("java.lang.Runnable", new ParameterInfo[0], false);
  }

  public void testReorderExceptions() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfo[0]),
           new SimpleExceptionsGen(new ThrownExceptionInfo[]{new ThrownExceptionInfo(1), new ThrownExceptionInfo(0)}),
           false);
  }

  public void testAlreadyHandled() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfo[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new ThrownExceptionInfo(-1, method.getManager().getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
           },
           false);
  }

  public void testAddRuntimeException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfo[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new ThrownExceptionInfo(-1, method.getManager().getElementFactory().createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
               };
             }
           },
           false);
  }

  public void testAddException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfo[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new ThrownExceptionInfo(-1, method.getManager().getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
           },
           false);
  }


  protected void setUpJdk() {
    super.setUpJdk();
    PsiManager.getInstance(myProject).setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }

  private void doTest(String newReturnType, ParameterInfo[] parameterInfos, final boolean generateDelegate) throws Exception {
    doTest(null, null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  private void doTest(String newVisibility,
                      String newName,
                      String newReturnType,
                      ParameterInfo[] parameterInfo,
                      ThrownExceptionInfo[] exceptionInfo,
                      final boolean generateDelegate) throws Exception {
    doTest(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo), new SimpleExceptionsGen(exceptionInfo), generateDelegate);
  }

  private void doTest(String newVisibility, String newName, String newReturnType, GenParams gen, final boolean generateDelegate) throws Exception {
    doTest(newVisibility, newName, newReturnType, gen, new SimpleExceptionsGen(), generateDelegate);
  }

  private void doTest(String newVisibility, String newName, String newReturnType, GenParams genParams, GenExceptions genExceptions, final boolean generateDelegate) throws Exception {
    final String filePath = "/refactoring/changeSignature/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiElementFactory factory = PsiManager.getInstance(getProject()).getElementFactory();
    PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType();
    new ChangeSignatureProcessor(getProject(), method, generateDelegate, newVisibility,
                                 newName != null ? newName : method.getName(),
                                 newType, genParams.genParams(method), genExceptions.genExceptions(method)).run();
    checkResultByFile(filePath + ".after");
  }

  private Editor getEditor() {
    return myEditor;
  }

  private interface GenParams {
    ParameterInfo[] genParams(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleParameterGen implements GenParams {
    private final ParameterInfo[] myInfos;

    private SimpleParameterGen(ParameterInfo[] infos) {
      myInfos = infos;
    }

    public ParameterInfo[] genParams(PsiMethod method) {
      for (ParameterInfo info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }

  private interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleExceptionsGen implements GenExceptions {
    private final ThrownExceptionInfo[] myInfos;

    public SimpleExceptionsGen() {
      myInfos = new ThrownExceptionInfo[0];
    }

    private SimpleExceptionsGen(ThrownExceptionInfo[] infos) {
      myInfos = infos;
    }

    public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
      for (ThrownExceptionInfo info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }
}
