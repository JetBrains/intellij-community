package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class DataEqualsAndHashCodeQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testClassWithDataExtendsObject() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import lombok.Data;

      @Data<caret>
      public class ClassWithDataExtendsObject {
          private String str;
      }
      """);

    assertFalse(hasActionWithText(),
                "Annotate class by '@EqualsAndHashCode' QuickFix should NOT be present");
  }

  @Test
  public void testClassWithDataExtendsAnotherClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
          static class SomeClassA {
            private int i;
          }

          @lombok.Data<caret>
          public class ClassWithDataExtendsAnotherClass extends SomeClassA {
              private String str;
          }
      """);

    assertTrue(hasActionWithText(),
               "Annotate class by '@EqualsAndHashCode' QuickFix should be present");
  }

  private boolean hasActionWithText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(LombokInspection.class);

    final Editor editor = myFixture.getEditor();
    final PsiFile file = myFixture.getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    final List<IntentionAction> availableActions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);

    return ContainerUtil.exists(availableActions, action -> action.getText().contains("'@EqualsAndHashCode'"));
  }
}
