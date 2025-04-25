package com.intellij.java.lomboktest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;

public class AdditionalLombokClassCanBeRecordInspectionTest extends AbstractLombokLightCodeInsightTestCase {
  private static final String VALUE_CLASS_SAMPLE = """
    @lombok.Value
    class Person<caret> {
      String name;
      int age;

      String getInfo() {
        return "Person " + name + " is " + age + " years old";
      }
    }

    class Foo {
      void foo() {
        Person person = new Person("Charlie", 42);
        // Renaming getters to accessors requires the Lombok plugin
        System.out.println("Person " + person.getName() + " is " + person.getAge() + " years old");
        System.out.println(person.getInfo());
      }
    }
    """;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  public void testClassCanBeRecord_renamingAccessors() {
    myFixture.enableInspections(new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true));
    myFixture.configureByText("Test.java", VALUE_CLASS_SAMPLE);

    IntentionAction intention = myFixture.findSingleIntention("Convert to record class");
    // myFixture.checkPreviewAndLaunchAction(intention); // preview differs from the actual result because of IDEA-369873
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> myFixture.launchAction(intention));
    myFixture.checkResult("""
                            record Person(String name, int age) {
                                String getInfo() {
                                    return "Person " + name + " is " + age + " years old";
                                }
                            }

                            class Foo {
                              void foo() {
                                Person person = new Person("Charlie", 42);
                                // Renaming getters to accessors requires the Lombok plugin
                                System.out.println("Person " + person.name() + " is " + person.age() + " years old");
                                System.out.println(person.getInfo());
                              }
                            }
                            """);
    myFixture.checkHighlighting(true, false, false); // Ensure again that code is well-formed
  }

  public void testClassCanBeRecord_notRenamingAccessors() {
    myFixture.enableInspections(new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, false));
    myFixture.configureByText("Test.java", VALUE_CLASS_SAMPLE);

    IntentionAction intention = myFixture.findSingleIntention("Convert to record class");
    // myFixture.checkPreviewAndLaunchAction(intention); // preview differs from the actual result because of IDEA-369873
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> myFixture.launchAction(intention));
    myFixture.checkResult("""
                            record Person(String name, int age) {
                                String getInfo() {
                                    return "Person " + name + " is " + age + " years old";
                                }

                                public String getName() {
                                    return this.name;
                                }

                                public int getAge() {
                                    return this.age;
                                }
                            }

                            class Foo {
                              void foo() {
                                Person person = new Person("Charlie", 42);
                                // Renaming getters to accessors requires the Lombok plugin
                                System.out.println("Person " + person.getName() + " is " + person.getAge() + " years old");
                                System.out.println(person.getInfo());
                              }
                            }
                            """);

    myFixture.checkHighlighting(true, false, false); // Ensure again that code is well-formed
  }
}
