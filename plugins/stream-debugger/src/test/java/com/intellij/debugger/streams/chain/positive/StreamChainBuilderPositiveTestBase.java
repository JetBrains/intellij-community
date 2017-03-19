package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.chain.StreamChainBuilderTestCase;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderPositiveTestBase extends StreamChainBuilderTestCase {

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain" + File.separator + "positive" + File.separator + getDirectoryName();
  }

  void doTest() throws Exception {
    final PsiElement elementAtCaret = configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final StreamChain chain = getChainBuilder().build(elementAtCaret);
    checkResultChain(chain);
  }



  @NotNull
  protected abstract String getDirectoryName();

  protected abstract void checkResultChain(StreamChain chain);
}
