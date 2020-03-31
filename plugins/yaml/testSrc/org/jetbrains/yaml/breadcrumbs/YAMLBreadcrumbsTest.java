// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.breadcrumbs;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class YAMLBreadcrumbsTest extends BasePlatformTestCase {
  private final String COPY_ACTION = YAMLBundle.message("YAMLBreadcrumbsInfoProvider.copy.key.to.clipboard");

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/breadcrumbs/data/";
  }

  public void testText() {
    doTest(crumb -> "[" + crumb.getText() + "]");
  }

  public void testCopyPathAction() {
    doTest(crumb -> applyActions(crumb, action -> getActionName(action).equals(COPY_ACTION)));
  }

  private void doTest(@NotNull Function<? super Crumb, String> crumbToString) {
    myFixture.configureByFile("all.yml");
    final CaretModel caretModel = myFixture.getEditor().getCaretModel();
    final String result = caretModel.getAllCarets().stream()
      .map(Caret::getOffset)
      .collect(Collectors.toList()).stream()
      .map((offset) -> {
        caretModel.moveToOffset(offset);
        return myFixture.getBreadcrumbsAtCaret().stream()
                        .map(crumbToString)
                        .reduce((left, right) -> left + right).orElse("[]");
      })
      .reduce((left, right) -> left + "\n------\n" + right).orElse("");

    assertSameLinesWithFile(getTestDataPath() + getTestName(true) + ".txt", result);
  }

  @NotNull
  private String applyAction(@NotNull Action action) {
    try {
      return applySingleAction(action);
    }
    catch (IOException | UnsupportedFlavorException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private String applySingleAction(@NotNull Action action) throws IOException, UnsupportedFlavorException {
    String actionName = getActionName(action);
    ActionEvent event = new ActionEvent(myFixture.getEditor().getComponent(), ActionEvent.ACTION_PERFORMED, actionName);

    if (actionName.equals(COPY_ACTION)) {
      action.actionPerformed(event);
      return (String)CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);
    }
    throw new IllegalArgumentException("Unknown action: " + actionName);
  }

  @NotNull
  private String applyActions(@NotNull Crumb crumb, Predicate<Action> filter) {
    String applied = crumb.getContextActions().stream().filter(filter).map(this::applyAction)
                          .reduce((left, right) -> left + ", " + right).orElse("");
    return "{" + applied + "}";
  }

  @NotNull
  private static String getActionName(@NotNull Action action) {
    Object actionNameValue = action.getValue("Name");
    assert actionNameValue instanceof String;
    return (String)actionNameValue;
  }
}
