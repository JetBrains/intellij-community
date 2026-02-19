// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class LombokRenameTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testLombokRenameFieldInBuilderWithPrefix() {

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

    myFixture.renameElementAtCaret("firmPartyGfcId_NewName");


    final PsiClass appBuilderClass = myFixture.findClass("App.AppBuilder");
    assertEquals(1, appBuilderClass.findMethodsByName("withFirmPartyGfcId_NewName", false).length);

    final PsiMethod doSomethingMethod = appBuilderClass.getContainingClass().findMethodsByName("doSomething", false)[0];
    assertTrue(doSomethingMethod.getBody().getText().contains("withFirmPartyGfcId_NewName(\"firm_party_gfcid\")"));
  }

  public void testLombokRenameFieldInBuilderWithoutPrefix() {

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

    myFixture.renameElementAtCaret("firmPartyGfcId_NewName");


    final PsiClass appBuilderClass = myFixture.findClass("App.AppBuilder");
    assertEquals(1, appBuilderClass.findMethodsByName("firmPartyGfcId_NewName", false).length);

    final PsiMethod doSomethingMethod = appBuilderClass.getContainingClass().findMethodsByName("doSomething", false)[0];
    assertTrue(doSomethingMethod.getBody().getText().contains("firmPartyGfcId_NewName(\"firm_party_gfcid\")"));
  }
}
