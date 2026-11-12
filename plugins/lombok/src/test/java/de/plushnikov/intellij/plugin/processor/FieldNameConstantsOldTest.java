package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok (1.18.2)
 */
@TestApplication
public class FieldNameConstantsOldTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR);
  //now incomplete mode is not supported for this processor, because it depends on the lombok version
  //after returning to normal mode, caches will be dropped
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest(incompleteMode = false)
  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    parsing.get().doTest(true);
  }
}
