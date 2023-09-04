package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.List;

public class SuperBuilderQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  public void testAddModifiersAbstractStaticOnInnerBuilderClass() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import lombok.experimental.SuperBuilder;

       @SuperBuilder
       class DeltaComponentWithSalesAndTechComponentId {
       }

       @SuperBuilder<caret>
       public class DeltaOfferComponent extends DeltaComponentWithSalesAndTechComponentId {

           Integer max;

           public class DeltaOfferComponentBuilder {
               public DeltaOfferComponentBuilder max(Integer max) {
                   this.max = max;
                   return this;
               }
           }
       }
      """);

    assertTrue(hasActionWithText("Make 'DeltaOfferComponentBuilder' abstract"));
    assertTrue(hasActionWithText("Make 'DeltaOfferComponentBuilder' static"));
  }

  private boolean hasActionWithText(String text) {
    myFixture.enableInspections(LombokInspection.class);

    final Editor editor = getEditor();
    final PsiFile file = getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    final List<IntentionAction> availableActions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);

    return ContainerUtil.exists(availableActions, action -> action.getText().contains(text));
  }
}
