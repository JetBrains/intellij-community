// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.MarkupSurroundTestFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.MarkupSurroundTestFixtureKt.markupSurroundFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/xml/tests/testData")
public class XmlSurroundWithTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<MarkupSurroundTestFixture> surroundFixture = markupSurroundFixture(codeInsightFixture);

  @Test
  public void testHtml() {
    surroundFixture.get().doSurroundWithTagTest("html");
  }

  @Test
  public void testHtml2() {
    surroundFixture.get().doSurroundWithTagTest("html");
  }

  @Test
  public void testHtml3() {
    surroundFixture.get().doSurroundWithTagTest("html");
  }

  @Test
  public void testHtml4() {
    doHtmlSurroundWithTagViaSurroundPopup("p");
  }

  @Test
  public void testHtml5() {
    doHtmlSurroundWithTagViaSurroundPopup("div");
  }

  @Test
  public void testXml() {
    surroundFixture.get().doSurroundWithTagTest("xml");
  }

  @Test
  public void testXml2() {
    surroundFixture.get().doSurroundWithCDataTest("xml");
  }

  @Test
  public void testXml3() {
    surroundFixture.get().doSurroundWithTagTest("xml");
  }

  @Test
  public void testMultiCaretSurroundWithCData() {
    surroundFixture.get().doSurroundWithCDataTest("xml");
  }

  @Test
  public void testSurroundWithTagFirstElement() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(MarkupSurroundTestFixture.BASE_PATH + "tag/Xml.xml");
    AnAction firstAction = EdtTestUtil.runInEdtAndGet(
      () -> SurroundWithTemplateHandler.createActionGroup(myFixture.getEditor(), myFixture.getFile(), new HashSet<>()).get(0));
    TemplateImpl template = assertInstanceOf(InvokeTemplateAction.class, firstAction).getTemplate();
    assertEquals("T", template.getKey());
  }

  @Test
  public void testSurroundWithAction() throws InterruptedException {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.html", "<selection>some text</selection>");
    myFixture.performEditorAction("SurroundWith");
    var ref = new Ref<PopupFactoryImpl.ActionGroupPopup>();
    UiInterceptors.register(new UiInterceptors.UiInterceptor<>(PopupFactoryImpl.ActionGroupPopup.class) {
      @Override
      protected void doIntercept(PopupFactoryImpl.@NotNull ActionGroupPopup component) {
        ref.set(component);
      }
    });
    EdtTestUtil.runInEdtAndWait(() -> {
      var startTime = System.currentTimeMillis();
      while (ref.isNull()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        //noinspection BusyWait
        Thread.sleep(1);
        if (startTime + 5000 < System.currentTimeMillis()) {
          throw new AssertionError("Popup not shown");
        }
      }
      try {
        var model = (ListPopupModel<?>)ref.get().getList().getModel();
        for (int i = 0; i < model.getSize(); i++) {
          if (model.getElementAt(i).toString().contains("Surround with <tag></tag>")) {
            return;
          }
        }
        throw new AssertionError("Surround with <tag></tag> action not found");
      } finally {
        PopupFactoryImpl.ActionGroupPopup disposable = ref.get();
        disposable.cancel();
        Disposer.dispose(disposable);
      }
    });
  }

  private void doHtmlSurroundWithTagViaSurroundPopup(String tag) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String baseName = surroundFixture.get().getBaseName("tag");
    myFixture.configureByFile(baseName + ".html");
    EdtTestUtil.runInEdtAndWait(() -> {
      List<AnAction> actions = SurroundWithHandler.buildSurroundActions(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      Optional<InvokeTemplateAction> surroundWithTagAction = actions.stream()
        .filter(it -> it instanceof InvokeTemplateAction)
        .map(it -> (InvokeTemplateAction)it)
        .filter(it -> it.getTemplateText().toLowerCase(Locale.ROOT).contains("surround with <tag>"))
        .findFirst();
      assertTrue(surroundWithTagAction.isPresent());
      surroundWithTagAction.get().perform();
    });
    myFixture.type(tag);
    myFixture.checkResultByFile(baseName + "_after.html");
  }
}
