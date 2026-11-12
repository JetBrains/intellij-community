package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class GetterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().compareCodeBlocks(false));

  @LombokParsingTest
  public void testGetter$GetterAccessLevel() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterAlreadyExists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterBoolean() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterDeprecated() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterEnum() {
    parsing.get().doTest(true);
  }

  public void ignore_testGetter$GetterLazy() {
    //TODO known problem, try to fix later
    parsing.get().doTest(true);
  }

  public void ignore_testGetter$GetterLazyBoolean() {
    //TODO known problem, try to fix later
    parsing.get().doTest(true);
  }

  public void ignore_testGetter$GetterLazyEahcToString() {
    //TODO known problem, try to fix later
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterLazyInvalid() {
    parsing.get().doTest(true);
  }

  public void ignore_testGetter$GetterLazyNative() {
    //TODO known problem, try to fix later
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterNone() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterOnClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterOnMethod() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterOnMethodErrors() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterOnMethodErrors2() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterOnStatic() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$GetterWithDollar() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$MultiFieldGetter() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$TrickyTypeResolution() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$ClassNamedAfterGetter() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$CommentsInterspersed() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testGetter$FluentAccessorsBoolean() {
    parsing.get().doTest(true);
  }
}
