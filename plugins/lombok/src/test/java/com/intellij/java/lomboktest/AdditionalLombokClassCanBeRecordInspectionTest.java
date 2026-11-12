package com.intellij.java.lomboktest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class AdditionalLombokClassCanBeRecordInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

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

  @Test
  public void testClassCanBeRecord_renamingAccessors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testClassCanBeRecord_notRenamingAccessors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
