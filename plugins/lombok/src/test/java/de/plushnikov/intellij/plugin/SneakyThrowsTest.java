package de.plushnikov.intellij.plugin;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * Based on logic from com.intellij.codeInsight.ExceptionCheckingTest
 */
public class SneakyThrowsTest extends LightJavaCodeInsightTestCase {

  public void testCatchAllException() {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCatchSpecificException() {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows(MyException.class) void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testCatchGeneralException() {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows(Exception.class) void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testNotCatchException() {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows(SomeException.class) void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("Test.MyException", exceptions.get(0).getCanonicalText());
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdk.getInstance().createJdk("java 1.8", "lib/mockJDK-1.8", false);
  }

  private PsiMethodCallExpression createCall(@NonNls final String body) {
    final PsiFile file = createFile("test.java", "class Test { " + body +
      "void throwsMyException() throws MyException {}" +
      "void throwsSomeException() throws SomeException {}" +
      "static class MyException extends Exception {}" +
      "static class SomeException extends Exception {}" +
      "static class Exception {}" +
      "}");
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);
    return methodCall;
  }

  private static PsiMethodCallExpression findMethodCall(PsiElement element) {
    if (element instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression) element;
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final PsiMethodCallExpression call = findMethodCall(child);
      if (call != null) {
        return call;
      }
    }

    return null;
  }
}
