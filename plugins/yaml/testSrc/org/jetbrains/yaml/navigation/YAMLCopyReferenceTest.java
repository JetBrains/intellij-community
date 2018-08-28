// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class YAMLCopyReferenceTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/navigation/data/";
  }

  public void testSimpleConfig() {
    doTest("top.next.targetKey");
  }

  public void testArrayInPath() {
    doTest("top.next.list[1].targetKey");
  }

  public void testExplicitKey() {
    doTest("top.next.several line targetKey");
  }

  public void testPlainTextValue() {
    checkEmptyReference();
  }

  public void testComment() {
    checkEmptyReference();
  }

  public void testFileNoReference() {
    myFixture.configureByFile("simpleConfig.yml");
    PsiElement element = myFixture.getElementAtCaret();
    PsiFile file = element.getContainingFile();
    assertInstanceOf(file, YAMLFile.class);
    String qualifiedName = CopyReferenceAction.elementToFqn(file);
    assertEquals("simpleConfig.yml", qualifiedName);
  }

  private void checkEmptyReference() {
    String reference = configureAndCopyReference();
    int line = myFixture.getEditor().getDocument().getLineNumber(myFixture.getCaretOffset());
    String expected = getTestName(true) + ".yml:" + (line + 1);
    assertEquals(expected, reference);
  }

  private void doTest(String result) {
    String reference = configureAndCopyReference();
    assertEquals(result, reference);
  }

  @NotNull
  private String configureAndCopyReference() {
    myFixture.configureByFile(getTestName(true) + ".yml");
    myFixture.performEditorAction(IdeActions.ACTION_COPY_REFERENCE);
    String reference;
    try {
      reference = (String)CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException e) {
      throw new RuntimeException(e);
    }
    return reference;
  }
}
