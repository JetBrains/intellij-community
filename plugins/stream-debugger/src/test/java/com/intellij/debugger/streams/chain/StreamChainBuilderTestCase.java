package com.intellij.debugger.streams.chain;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.JdkManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderTestCase extends LightCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return new File("testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JdkManager.getMockJdk18();
  }

  @NotNull
  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @NotNull
  protected PsiElement configureAndGetElementAtCaret() {
    final String name = File.separator + getTestName(false) + ".java";
    configureByFile(name);
    final PsiFile file = getFile();
    final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
    final PsiElement elementAtCaret = DebuggerUtilsEx.findElementAt(file, offset);
    assertNotNull(elementAtCaret);
    return elementAtCaret;
  }

  @NotNull
  protected abstract String getRelativeTestPath();
}
