package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * The fix for {@code @Builder.Default} on a class without a builder annotation adds the {@code @SuperBuilder} that the Lombok of the module has.
 */
public class BuilderDefaultQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return getTestName(false).contains("OldLombok") ? LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR : super.getProjectDescriptor();
  }

  public void testAddSuperBuilderInLombokPackage() {
    doTest("lombok.SuperBuilder");
  }

  public void testAddSuperBuilderInExperimentalPackageForOldLombok() {
    doTest("lombok.experimental.SuperBuilder");
  }

  private void doTest(@NotNull String expectedImport) {
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
    assertNotNull("No intention adds @SuperBuilder", action);
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
