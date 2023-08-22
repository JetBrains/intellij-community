package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.List;

public class DataEqualsAndHashCodeQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  public void testClassWithDataExtendsObject() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import lombok.Data;

      @Data<caret>
      public class ClassWithDataExtendsObject {
          private String str;
      }
      """);

    assertFalse("Annotate class by '@EqualsAndHashCode' QuickFix should NOT be present",
                hasActionWithText());
  }

  public void testClassWithDataExtendsAnotherClass() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
          static class SomeClassA {
            private int i;
          }

          @lombok.Data<caret>
          public class ClassWithDataExtendsAnotherClass extends SomeClassA {
              private String str;
          }
      """);

    assertTrue("Annotate class by '@EqualsAndHashCode' QuickFix should be present",
               hasActionWithText());
  }

  private boolean hasActionWithText() {
    myFixture.enableInspections(LombokInspection.class);

    final Editor editor = getEditor();
    final PsiFile file = getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    final List<IntentionAction> availableActions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);

    return ContainerUtil.exists(availableActions, action -> action.getText().contains("'@EqualsAndHashCode'"));
  }
}
