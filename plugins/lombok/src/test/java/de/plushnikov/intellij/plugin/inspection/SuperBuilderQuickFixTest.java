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
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SuperBuilderQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testAddModifiersAbstractAndStaticOnInnerBuilderClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(LombokInspection.class);
    @Language("JAVA") final String text = """
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
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    IntentionAction action = myFixture.findSingleIntention("Make 'DeltaOfferComponentBuilder' abstract and static");
    myFixture.launchAction(action);
    myFixture.checkResult("""
                            import lombok.experimental.SuperBuilder;

                             @SuperBuilder
                             class DeltaComponentWithSalesAndTechComponentId {
                             }

                             @SuperBuilder
                             public class DeltaOfferComponent extends DeltaComponentWithSalesAndTechComponentId {

                                 Integer max;

                                 public abstract static class DeltaOfferComponentBuilder {
                                     public DeltaOfferComponentBuilder max(Integer max) {
                                         this.max = max;
                                         return this;
                                     }
                                 }
                             }
                            """);
  }

  @Test
  public void testAddModifiersAbstractOnInnerBuilderClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") final String text = """
      import lombok.experimental.SuperBuilder;

       @SuperBuilder
       class DeltaComponentWithSalesAndTechComponentId {
       }

       @SuperBuilder<caret>
       public class DeltaOfferComponent extends DeltaComponentWithSalesAndTechComponentId {

           Integer max;

           public static class DeltaOfferComponentBuilder {
               public DeltaOfferComponentBuilder max(Integer max) {
                   this.max = max;
                   return this;
               }
           }
       }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    assertTrue(hasActionWithText("Make 'DeltaOfferComponentBuilder' abstract"));
  }

  @Test
  public void testAddModifiersStaticOnInnerBuilderClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") final String text = """
      import lombok.experimental.SuperBuilder;

       @SuperBuilder
       class DeltaComponentWithSalesAndTechComponentId {
       }

       @SuperBuilder<caret>
       public class DeltaOfferComponent extends DeltaComponentWithSalesAndTechComponentId {

           Integer max;

           public abstract class DeltaOfferComponentBuilder {
               public DeltaOfferComponentBuilder max(Integer max) {
                   this.max = max;
                   return this;
               }
           }
       }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    assertTrue(hasActionWithText("Make 'DeltaOfferComponentBuilder' static"));
  }

  private boolean hasActionWithText(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(LombokInspection.class);

    final Editor editor = myFixture.getEditor();
    final PsiFile file = myFixture.getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    final List<IntentionAction> availableActions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);

    return ContainerUtil.exists(availableActions, action -> action.getText().contains(text));
  }
}
