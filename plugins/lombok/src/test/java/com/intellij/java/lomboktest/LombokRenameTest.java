// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ModPlanRenameTestSupport;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LombokRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testLombokRenameFieldInBuilderWithPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    final @Language("JAVA") String testFileData = """
      import lombok.Builder;
      import lombok.Data;
      import lombok.Singular;

      import java.util.List;

       @Data
       @Builder(setterPrefix = "with")
       public class App {

           private String firmPartyGfcId<caret>;

           @Singular
           private List<String> someStrValues;

           public static App doSomething() {
               return App.builder()
                       .withFirmPartyGfcId("firm_party_gfcid")
                       .withSomeStrValue("someValue")
                       .withSomeStrValues(List.of("xyz"))
                       .clearSomeStrValues()
                       .build();
           }
       }""";
    myFixture.configureByText(JavaFileType.INSTANCE, testFileData);

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaret("firmPartyGfcId_NewName");

      final PsiClass appBuilderClass = myFixture.findClass("App.AppBuilder");
      assertEquals(1, appBuilderClass.findMethodsByName("withFirmPartyGfcId_NewName", false).length);

      final PsiMethod doSomethingMethod = appBuilderClass.getContainingClass().findMethodsByName("doSomething", false)[0];
      assertTrue(doSomethingMethod.getBody().getText().contains("withFirmPartyGfcId_NewName(\"firm_party_gfcid\")"));
    });
  }

  @Test
  public void testLombokRenameFieldRenamesGetterCallInOtherClass() {
    LightJavaCodeInsightTestFixture myFixture = configureGetterCaller();

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaret("title");

      assertGetterCallRenamed(myFixture);
    });
  }

  @Test
  public void testLombokRenameFieldRenamesGetterCallInOtherClassOnModPlan() {
    LightJavaCodeInsightTestFixture myFixture = configureGetterCaller();

    EdtTestUtil.runInEdtAndWait(() -> {
      ModPlanRenameTestSupport.renameOnModPlan(myFixture.getProject(), myFixture.getElementAtCaret(), "title", ModPlanOptionValues.EMPTY);

      assertGetterCallRenamed(myFixture);
    });
  }

  private LightJavaCodeInsightTestFixture configureGetterCaller() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      public class Caller {
        String read(Person person) {
          return person.getName();
        }
      }""");
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import lombok.Getter;

      @Getter
      public class Person {
        private String na<caret>me;
      }""");
    return myFixture;
  }

  private static void assertGetterCallRenamed(LightJavaCodeInsightTestFixture myFixture) {
    assertEquals(1, myFixture.findClass("Person").findMethodsByName("getTitle", false).length);
    assertTrue(myFixture.findClass("Caller").getText().contains("person.getTitle()"));
  }

  @Test
  public void testLombokRenameFieldInBuilderWithoutPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    final @Language("JAVA") String testFileData = """
      import lombok.Builder;
      import lombok.Data;
      import lombok.Singular;

      import java.util.List;

       @Data
       @Builder
       public class App {

           private String firmPartyGfcId<caret>;

           @Singular
           private List<String> someStrValues;

           public static App doSomething() {
               return App.builder()
                       .firmPartyGfcId("firm_party_gfcid")
                       .someStrValue("someValue")
                       .someStrValues(List.of("xyz"))
                       .clearSomeStrValues()
                       .build();
           }
       }""";
    myFixture.configureByText(JavaFileType.INSTANCE, testFileData);

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaret("firmPartyGfcId_NewName");

      final PsiClass appBuilderClass = myFixture.findClass("App.AppBuilder");
      assertEquals(1, appBuilderClass.findMethodsByName("firmPartyGfcId_NewName", false).length);

      final PsiMethod doSomethingMethod = appBuilderClass.getContainingClass().findMethodsByName("doSomething", false)[0];
      assertTrue(doSomethingMethod.getBody().getText().contains("firmPartyGfcId_NewName(\"firm_party_gfcid\")"));
    });
  }
}
