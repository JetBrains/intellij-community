package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

/**
 * Tests for LombokBuilderCompletionContributor — verifies that a single special
 * lookup item "Complete 'Builder.build()'" is suggested and that selecting it
 * inserts the full builder call chain with placeholders, matching the existing
 * intention tests' expected "after" files.
 */
public class LombokBuilderCompletionContributorTest extends AbstractLombokLightCodeInsightTestCase {

  private static final String LOOKUP_TEXT = "Complete 'Builder.build()'"; // LombokBundle.complete.all.builder.methods.lombok.completion

  private String expectedChain; // set per-test

  // Basic @Builder on class
  public void testBasicBuilder() {
    String src = """
      import lombok.Builder;

      @Builder
      public class BasicBuilder {
        private String name;
        private Integer age;
        private Boolean active;

        public static void main(String[] args) {
          BasicBuilder.builder().<caret>;
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BasicBuilder.java", src);
    doCompletionAndApply();
  }

  // @SuperBuilder on class (should include parent field too)
  public void testSuperBuilderSimple() {
    String src = """
      import lombok.experimental.SuperBuilder;

      @SuperBuilder
      public class SuperBuilderSimple extends ParentClass {
        private String name;
        private Integer age;
        private Boolean active;

        public static void main(String[] args) {
          SuperBuilderSimple.builder().<caret>;
        }
      }

      @SuperBuilder
      class ParentClass {
        private String parentField;
      }
      """;
    expectedChain = ".builder().name().age().active().parentField().build();";
    myFixture.configureByText("SuperBuilderSimple.java", src);
    doCompletionAndApply();
  }

  // @Builder with @NonNull field
  public void testBuilderWithNotNull() {
    String src = """
      import lombok.Builder;
      import lombok.NonNull;

      @Builder
      public class BuilderWithNotNull {
        private String name;
        @NonNull
        private Integer age;
        private Boolean active;

        public static void main(String[] args) {
          BuilderWithNotNull.builder().<caret>;
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BuilderWithNotNull.java", src);
    doCompletionAndApply();
  }

  // @Builder with @Accessors(prefix = "m")
  public void testBuilderWithAccessorsPrefix() {
    String src = """
      import lombok.Builder;
      import lombok.experimental.Accessors;

      @Builder
      @Accessors(prefix = "m")
      public class BuilderWithAccessorsPrefix {
        private String mName;
        private Integer mAge;
        private Boolean mActive;

        public static void main(String[] args) {
          BuilderWithAccessorsPrefix.builder().<caret>;
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BuilderWithAccessorsPrefix.java", src);
    doCompletionAndApply();
  }

  // Custom builder/build method names
  public void testBuilderWithCustomNames() {
    String src = """
      import lombok.Builder;

      @Builder(builderMethodName = "create", buildMethodName = "construct")
      public class BuilderWithCustomNames {
        private String name;
        private Integer age;
        private Boolean active;

        public static void main(String[] args) {
          BuilderWithCustomNames.create().<caret>;
        }
      }
      """;
    expectedChain = ".create().name().age().active().construct();";
    myFixture.configureByText("BuilderWithCustomNames.java", src);
    doCompletionAndApply();
  }

  // @Builder on constructor
  public void testBuilderOnConstructor() {
    String src = """
      import lombok.Builder;

      public class BuilderOnConstructor {
        private String name;
        private Integer age;
        private Boolean active;

        @Builder
        public BuilderOnConstructor(String name, Integer age, Boolean active) {
          this.name = name;
          this.age = age;
          this.active = active;
        }

        public static void main(String[] args) {
          BuilderOnConstructor.builder().<caret>;
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BuilderOnConstructor.java", src);
    doCompletionAndApply();
  }

  // @Builder on static factory method
  public void testBuilderOnStaticMethod() {
    String src = """
      import lombok.Builder;

      public class BuilderOnStaticMethod {
        private String name;
        private Integer age;
        private Boolean active;

        private BuilderOnStaticMethod(String name, Integer age, Boolean active) {
          this.name = name;
          this.age = age;
          this.active = active;
        }

        @Builder
        public static BuilderOnStaticMethod createInstance(String name, Integer age, Boolean active) {
          return new BuilderOnStaticMethod(name, age, active);
        }

        public static void main(String[] args) {
          BuilderOnStaticMethod.builder().<caret>;
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BuilderOnStaticMethod.java", src);
    doCompletionAndApply();
  }

  // Predefined builder class present
  public void testBuilderWithPredefinedBuilder() {
    String src = """
      import lombok.Builder;

      @Builder
      public class BuilderWithPredefinedBuilder {
        private String name;
        private Integer age;
        private Boolean active;

        public static void main(String[] args) {
          BuilderWithPredefinedBuilder.builder().<caret>;
        }

        public static class BuilderWithPredefinedBuilderBuilder {
          private String customField;

          public BuilderWithPredefinedBuilderBuilder customMethod() {
            this.customField = "custom";
            return this;
          }
        }
      }
      """;
    expectedChain = ".builder().name().age().active().build();";
    myFixture.configureByText("BuilderWithPredefinedBuilder.java", src);
    doCompletionAndApply();
  }

  private void doCompletionAndApply() {
    // Invoke completion
    myFixture.complete(CompletionType.BASIC);

    // Ensure our special lookup appears (by presentable text, not lookup string)
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull("No completion suggestions were produced", elements);

    LookupElement toChoose = ContainerUtil.find(elements, le -> {
      LookupElementPresentation p = new LookupElementPresentation();
      le.renderElement(p);
      return LOOKUP_TEXT.equals(p.getItemText());
    });

    assertNotNull("Expected lookup item not found by presentable text: " + LOOKUP_TEXT, toChoose);

    // Select the specific lookup item and apply it
    myFixture.getLookup().setCurrentItem(toChoose);
    myFixture.type('\n');

    // Finish the live template by tabbing through all variables
    com.intellij.codeInsight.template.impl.TemplateState state = com.intellij.codeInsight.template.impl.TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    int guard = 0;
    while (state != null && !state.isFinished() && guard < 20) {
      state.nextTab();
      state = com.intellij.codeInsight.template.impl.TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      guard++;
    }

    // Assert that the resulting code contains the expected builder chain
    String actual = myFixture.getEditor().getDocument().getText();
    assertTrue("Result does not contain expected builder chain: " + expectedChain + "\nActual: " + actual,
      actual.contains(expectedChain));
  }
}
