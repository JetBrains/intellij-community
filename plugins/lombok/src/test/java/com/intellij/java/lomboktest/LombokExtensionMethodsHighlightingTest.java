package com.intellij.java.lomboktest;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokDaemonAnalyzerFixture;

@TestApplication
public class LombokExtensionMethodsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lombokDaemonAnalyzerFixture(projectFixture, "/highlighting/extensionMethods/");

  @Test
  public void testArray() { doTest(); }
  @Test
  public void testNestedExtensions() { doTest(); }
  @Test
  public void testAssignability() { doTest(); }
  @Test
  public void testConflictingOwnMethod() { doTest(); }

  @Test
  public void testExtensionMethodAutoboxing() { doTest(); }

  @Test
  public void testExtensionMethodFunctional() { doTest(); }
  @Test
  public void testExtensionMethodNames() { doTest(); }
  @Test
  public void testExtensionMethodPlain() { doTest(); }
  @Test
  public void testExtensionMethodSuppress() { doTest(); }
  @Test
  public void testExtensionMethodVarargs() { doTest(); }
  @Test
  public void testExtensionMethodVarargsOverloaded() { doTest(); }
  @Test
  public void testExtensionMethodWrongArguments() { doTest(); }

  private void doTest() {
    LightDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(fixture.getFixture().getTestName(false) + ".java", true, false);
  }
}
