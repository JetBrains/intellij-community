package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class RemoveAnnotationQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(LombokInspection.class);
  }

  @Test
  public void testRedundant_ToString() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testInvalid_Data() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.Data
      record Person4(String name, int age) {
      }""");

    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction action = myFixture.findSingleIntention("Remove annotation");
      myFixture.checkPreviewAndLaunchAction(action);

      myFixture.checkResult("""
                              record Person4(String name, int age) {
                              }""");

      myFixture.checkHighlighting();
    });
  }

  @Test
  public void testInvalid_Value() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.Value
      record Person4(String name, int age) {
      }""");

    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction action = myFixture.findSingleIntention("Remove annotation");
      myFixture.checkPreviewAndLaunchAction(action);

      myFixture.checkResult("""
                              record Person4(String name, int age) {
                              }""");

      myFixture.checkHighlighting();
    });
  }

  @Test
  public void testInvalid_NoArgsConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.NoArgsConstructor
      class Person {
          String name;
          int age;

          public Person() {
          }
      }""");

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testInvalid_RequiredArgsConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      @lombok.RequiredArgsConstructor
      class Person {
          final String name;
          int age;

          public Person(String name) {
              this.name = name;
          }
      }""");

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testInvalid_AllArgsConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }
}
