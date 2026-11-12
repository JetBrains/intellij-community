// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.template.emmet.EmmetPreviewTestFixture.emmetPreviewFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class HtmlEmmetPreviewTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EmmetPreviewTestFixture> emmet = emmetPreviewFixture(projectFixture);

  @Test
  public void testPrimitiveAbbreviation() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.html", "di<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("v");
      assertNull(preview.getPreview());
    });
  }

  @Test
  public void testPrimitiveAbbreviationWithEmptyClass() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.html", "div<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type(".");
      assertNull(preview.getPreview());
    });
  }

  @Test
  public void testAbbreviationWithNonEmptyClass() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.html", "div.<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("c");
      preview.assertPreview("<div class=\"c\"></div>");
    });
  }

  @Test
  public void testAbbreviationWithNesting() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.html", "div>di<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("v");
      preview.assertPreview("""
                              <div>
                                  <div></div>
                              </div>""");
    });
  }

  @Test
  public void testAbbreviationWithFilter() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.html", "div#id>div.class<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("|c");
      preview.assertPreview("""
                              <div id="id">
                                  <div class="class"></div>
                                  <!-- /.class -->
                              </div>
                              <!-- /#id -->""");
      myFixture.type("|s");
      preview.assertPreview("""
                              <div id="id">
                                  <div class="class"></div>
                                  <!-- /.class -->
                              </div>
                              <!-- /#id -->""");
    });
  }

  @Test
  public void testPreviewXhtml() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.xhtml", "div>b<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("r");
      preview.assertPreview("<div><br/></div>");
    });
  }

  @Test
  public void testNoPreviewInXmlAttributeWithoutInjection() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    myFixture.configureByText("test.xml", "<root html=\"div.<caret>\"/>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("c");
      preview.assertNoPreview();
    });
  }

  @Test
  public void testPreviewInInjectedHtml() {
    EmmetPreviewTestFixture preview = emmet.get();
    CodeInsightTestFixture myFixture = preview.getFixture();
    injectHtmlIntoAttribute();
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByText("test.xml", "<root html=\"div.<caret>\"/>");
    ReadAction.runBlocking(() -> {
      PsiElement injected = InjectedLanguageManager.getInstance(preview.getProject())
        .findInjectedElementAt(myFixture.getFile(), myFixture.getCaretOffset() - 1);
      assertNotNull(injected);
      assertEquals(HTMLLanguage.INSTANCE, injected.getContainingFile().getLanguage());
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("c");
      preview.assertPreview("<div class='c'></div>");
    });
  }

  private void injectHtmlIntoAttribute() {
    MultiHostInjector injector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlAttributeValue value) || !(value.getParent() instanceof XmlAttribute attribute)) return;
        if (!"html".equals(attribute.getName())) return;
        registrar.startInjecting(HTMLLanguage.INSTANCE)
          .addPlace(null, null, (PsiLanguageInjectionHost)value, ElementManipulators.getValueTextRange(value))
          .doneInjecting();
      }

      @Override
      public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlAttributeValue.class);
      }
    };
    InjectedLanguageManager.getInstance(emmet.get().getProject())
      .registerMultiHostInjector(injector, emmet.get().getFixture().getTestRootDisposable());
  }
}
