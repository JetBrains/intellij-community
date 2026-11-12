package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The fix for {@code @Builder.Default} on a class without a builder annotation adds the {@code @SuperBuilder} that the Lombok of the module has.
 */
@TestApplication
public class BuilderDefaultQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, BuilderDefaultQuickFixTest::descriptor);

  private static @NotNull LightProjectDescriptor descriptor(@NotNull String testName) {
    return testName.contains("OldLombok") ? LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR : LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  @Test
  public void testAddSuperBuilderInLombokPackage() {
    doTest("lombok.SuperBuilder");
  }

  @Test
  public void testAddSuperBuilderInExperimentalPackageForOldLombok() {
    doTest("lombok.experimental.SuperBuilder");
  }

  private void doTest(@NotNull String expectedImport) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(LombokInspection.class);
    @Language("JAVA") String text = """
      import lombok.Builder;

      public class Foo {
        @Builder.Default<caret>
        private int value = 1;
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    IntentionAction action = ContainerUtil.find(myFixture.getAvailableIntentions(), it -> it.getText().contains("SuperBuilder"));
    assertNotNull(action, "No intention adds @SuperBuilder");
    myFixture.launchAction(action);
    myFixture.checkResult("""
                            import lombok.Builder;
                            import %s;

                            @SuperBuilder
                            public class Foo {
                              @Builder.Default
                              private int value = 1;
                            }
                            """.formatted(expectedImport));
  }
}
