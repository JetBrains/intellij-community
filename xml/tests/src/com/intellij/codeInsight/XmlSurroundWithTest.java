// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class XmlSurroundWithTest extends MarkupSurroundTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testHtml() {
    doSurroundWithTagTest("html");
  }

  public void testHtml2() {
    doSurroundWithTagTest("html");
  }

  public void testHtml3() {
    doSurroundWithTagTest("html");
  }

  public void testHtml4() {
    doHtmlSurroundWithTagViaSurroundPopup("p");
  }

  public void testHtml5() {
    doHtmlSurroundWithTagViaSurroundPopup("div");
  }

  public void testXml() {
    doSurroundWithTagTest("xml");
  }

  public void testXml2() {
    doSurroundWithCDataTest("xml");
  }

  public void testXml3() {
    doSurroundWithTagTest("xml");
  }

  public void testMultiCaretSurroundWithCData() {
    doSurroundWithCDataTest("xml");
  }

  public void testSurroundWithTagFirstElement() {
    myFixture.configureByFile(BASE_PATH + "tag/Xml.xml");
    AnAction firstAction =
      SurroundWithTemplateHandler.createActionGroup(myFixture.getEditor(), myFixture.getFile(), new HashSet<>()).get(0);
    assertInstanceOf(firstAction, InvokeTemplateAction.class);
    TemplateImpl template = ((InvokeTemplateAction)firstAction).getTemplate();
    assertEquals("T", template.getKey());
  }

  public void testSurroundWithAction() throws InterruptedException {
    myFixture.configureByText("foo.html", "<selection>some text</selection>");
    myFixture.performEditorAction("SurroundWith");
    var ref = new Ref<PopupFactoryImpl.ActionGroupPopup>();
    UiInterceptors.register(new UiInterceptors.UiInterceptor<>(PopupFactoryImpl.ActionGroupPopup.class) {
      @Override
      protected void doIntercept(PopupFactoryImpl.@NotNull ActionGroupPopup component) {
        ref.set(component);
      }
    });
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
  }

  private void doHtmlSurroundWithTagViaSurroundPopup(String tag) {
    String baseName = getBaseName("tag");
    myFixture.configureByFile(baseName + ".html");
    List<AnAction> actions = SurroundWithHandler.buildSurroundActions(getProject(), myFixture.getEditor(), myFixture.getFile());
    Optional<InvokeTemplateAction> surroundWithTagAction = actions.stream()
      .filter(it -> it instanceof InvokeTemplateAction)
      .map(it -> (InvokeTemplateAction)it)
      .filter(it -> it.getTemplateText().toLowerCase(Locale.ROOT).contains("surround with <tag>"))
      .findFirst();
    assertTrue(surroundWithTagAction.isPresent());
    surroundWithTagAction.get().perform();
    myFixture.type(tag);
    myFixture.checkResultByFile(baseName + "_after.html");
  }
}
