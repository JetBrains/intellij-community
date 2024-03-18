package de.plushnikov.intellij.plugin;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public void testLambdaSneakyThrowsWrongCatch() {
    PsiFile file = createTestFile("""
                                    @lombok.SneakyThrows    public void m1() {
                                            Runnable runnable = () -> {
                                                throwsMyException();        };
                                        }
                                    """);
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("Test.MyException", exceptions.get(0).getCanonicalText());
  }

  public void testAnonymousClassCorrectCatch() {
    PsiFile file = createTestFile("""
                                    @lombok.SneakyThrows    public void m1() {
                                            Runnable runnable = new Runnable() {
                                               public void run() { throwsMyException(); }        };
                                        }
                                    """);
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("Test.MyException", exceptions.get(0).getCanonicalText());
  }

  public void testTryCatchThatCatchAnotherException() {
    PsiFile file = createTestFile("""
                                    @lombok.SneakyThrows
                                        public void m() {
                                            try {
                                                throwsMyException();            throwsSomeException();        } catch (Test.SomeException e) {
                                            }
                                        }""");

    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);

    PsiTryStatement tryStatement = findFirstChild(file, PsiTryStatement.class);
    assertNotNull(tryStatement);

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, tryStatement);
    assertSize(0, exceptions);
  }

  public void testTryCatchThatCatchAnotherExceptionWithNullTopElement() {
    PsiMethodCallExpression methodCall = createCall("""
                                                      @lombok.SneakyThrows
                                                          public void m() {
                                                              try {
                                                                  throwsMyException();            throwsSomeException();        } catch (Test.SomeException e) {
                                                              }
                                                          }""");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertSize(0, exceptions);
  }

  public void testTryCatchThatCatchAnotherExceptionHierarchy() {
    PsiFile file = createTestFile("""
                                    @lombok.SneakyThrows
                                        public void m() {
                                            try {
                                                try {                throwsMyException();
                                                    throwsSomeException();                throwsAnotherException();            } catch (Test.SomeException e) {}
                                            } catch (Test.AnotherException e) {}
                                        }""");

    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);

    PsiTryStatement tryStatement = findFirstChild(file, PsiTryStatement.class);
    assertNotNull(tryStatement);

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertSize(0, exceptions);
  }

  /**
   * to avoid catching all exceptions by default by accident
   */
  public void testRegularThrows() {
    PsiMethodCallExpression methodCall = createCall("void foo() {  throwsMyException(); }");
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(methodCall, null);
    assertEquals(1, exceptions.size());
    assertEquals("Test.MyException", exceptions.get(0).getCanonicalText());
  }



  private PsiMethodCallExpression createCall(@NonNls @Language("JAVA") final String body) {
    final PsiFile file = createTestFile(body);
    PsiMethodCallExpression methodCall = findMethodCall(file);
    assertNotNull(methodCall);
    return methodCall;
  }

  @NotNull
  private PsiFile createTestFile(@NonNls @Language("JAVA") String body) {
    return createFile("test.java", "class Test { " + body +
      "void throwsAnotherException() throws AnotherException {}" +
      "void throwsMyException() throws MyException {}" +
      "void throwsSomeException() throws SomeException {}" +
      "static class MyException extends Exception {}" +
      "static class SomeException extends Exception {}" +
      "static class AnotherException extends Exception {}" +
      "static class Exception{}" +
      "}");
  }

  @Nullable
  private static PsiMethodCallExpression findMethodCall(@NotNull PsiElement element) {
    return findFirstChild(element, PsiMethodCallExpression.class);
  }

  @Nullable
  private static <T extends PsiElement> T findFirstChild(@NotNull PsiElement element, Class<T> aClass) {
    if (aClass.isInstance(element)) {
      return (T) element;
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final T call = findFirstChild(child, aClass);
      if (call != null) {
        return call;
      }
    }

    return null;
  }
}
