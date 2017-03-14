package com.intellij.debugger.streams;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainBuilderPositiveTest extends StreamChainBuilderFixtureTestCase {

  public void testSimple() throws Exception {
    doTest();
  }

  public void testHard() throws Exception {
    doTest();
  }

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain/positive/";
  }

  private void doTest() throws Exception {
    final String name = File.separator + getTestName(false) + ".java";
    configureByFile(name);
    final PsiFile file = getFile();
    final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
    final PsiElement elementAtCaret = DebuggerUtilsEx.findElementAt(file, offset);
    assertNotNull(elementAtCaret);
    final StreamChain chain = StreamChainBuilder.tryBuildChain(elementAtCaret);
    Assert.assertNotNull(chain);
  }
}
