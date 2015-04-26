package de.plushnikov.lombok;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class SneakyThrowsTest extends LightCodeInsightTestCase {

  public void testCatchException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertTrue(exceptions.isEmpty());
  }

  public void testNotCatchException() throws Exception {
    PsiMethodCallExpression methodCall = createCall("@lombok.SneakyThrows(SomeException.class) void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("Test.MyException", exceptions.get(0).getCanonicalText());
  }


  private static PsiMethodCallExpression createCall(@NonNls final String body) throws Exception {
    final PsiFile file = createFile("test.java", "class Test { " + body +
        "void throwsMyException() throws MyException {}" +
        "static class MyException {}" +
        "static class SomeException {}" +
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
