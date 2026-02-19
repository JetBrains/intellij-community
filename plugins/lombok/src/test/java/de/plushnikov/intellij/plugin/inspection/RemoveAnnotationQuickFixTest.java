package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class RemoveAnnotationQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(LombokInspection.class);
  }

  public void testRedundant_ToString() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.ToString
      class Person_D {
          String name;
          int age;

          @Override
          public String toString() {
              return "Person_D{name='" + name + "', age=" + age + '}';
          }
      }""");


    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            class Person_D {
                                String name;
                                int age;

                                @Override
                                public String toString() {
                                    return "Person_D{name='" + name + "', age=" + age + '}';
                                }
                            }""");

    myFixture.checkHighlighting();
  }

  public void testInvalid_Data() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.Data
      record Person4(String name, int age) {
      }""");


    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            record Person4(String name, int age) {
                            }""");

    myFixture.checkHighlighting();
  }

  public void testInvalid_Value() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.Value
      record Person4(String name, int age) {
      }""");


    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            record Person4(String name, int age) {
                            }""");

    myFixture.checkHighlighting();
  }


  public void testInvalid_NoArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.NoArgsConstructor
      class Person {
          String name;
          int age;

          public Person() {
          }
      }""");

    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            class Person {
                                String name;
                                int age;

                                public Person() {
                                }
                            }""");

    myFixture.checkHighlighting();
  }

  public void testInvalid_RequiredArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.RequiredArgsConstructor
      class Person {
          final String name;
          int age;

          public Person(String name) {
              this.name = name;
          }
      }""");

    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            class Person {
                                final String name;
                                int age;

                                public Person(String name) {
                                    this.name = name;
                                }
                            }""");

    myFixture.checkHighlighting();
  }

  public void testInvalid_AllArgsConstructor() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.AllArgsConstructor
      class Person {
          String name;
          int age;

          public Person(String name, int age) {
              this.name = name;
              this.age = age;
          }
      }""");

    IntentionAction action = myFixture.findSingleIntention("Remove annotation");
    myFixture.checkPreviewAndLaunchAction(action);

    myFixture.checkResult("""
                            class Person {
                                String name;
                                int age;

                                public Person(String name, int age) {
                                    this.name = name;
                                    this.age = age;
                                }
                            }""");

    myFixture.checkHighlighting();
  }
}
