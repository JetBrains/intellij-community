package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderToBuilderTest extends AbstractLombokParsingTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  public void testBuilder$BuilderWithToBuilder() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnClass() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnConstructor() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithToBuilderOnMethod() throws IOException {
    doTest(true);
  }

//  public void testBuilderTime() throws InvocationTargetException, IllegalAccessException {
//    final String testName = "builder$BuilderWithToBuilderOnMethod";
//    final PsiJavaFile beforeLombokFile = loadBeforeLombokFile(testName);
//    final PsiJavaFile afterDelombokFile = loadAfterDeLombokFile(testName);
//
//    final PsiClass[] beforeLombokFileClasses = beforeLombokFile.getClasses();
//
//    final Method clearUserDataMethod = ReflectionUtil.getDeclaredMethod(UserDataHolderBase.class, "clearUserData");
//    for (int i = 0; i < 20; i++) {
//      compareFiles(beforeLombokFile, afterDelombokFile);
//      clearUserDataMethod.invoke(beforeLombokFileClasses[0]);
//    }
//
////    PlatformTestUtil.startPerformanceTest("Test Performance", 100, new ThrowableRunnable() {
////      @Override
////      public void run() throws Throwable {
////        compareFiles(beforeLombokFile, afterDelombokFile);
////        clearUserDataMethod.invoke(beforeLombokFileClasses[0]);
////      }
////    }).useLegacyScaling().attempts(3).assertTiming();
//  }
}
