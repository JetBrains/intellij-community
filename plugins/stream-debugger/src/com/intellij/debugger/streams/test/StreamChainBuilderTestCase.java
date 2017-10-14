// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.test;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl;
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderTestCase extends LightCodeInsightTestCase {
  private final StreamChainBuilder myBuilder = new JavaStreamChainBuilder(new JavaChainTransformerImpl(), "java.util.stream");

  @NotNull
  @Override
  protected String getTestDataPath() {
    return new File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/" + getRelativeTestPath()).getAbsolutePath();
  }

  @NotNull
  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @NotNull
  protected PsiElement configureAndGetElementAtCaret() {
    final String name = File.separator + getTestName(false) + getFileExtension();
    configureByFile(name);
    final PsiFile file = getFile();
    final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
    final PsiElement elementAtCaret = DebuggerUtilsEx.findElementAt(file, offset);
    assertNotNull(elementAtCaret);
    return elementAtCaret;
  }

  @NotNull
  protected StreamChainBuilder getChainBuilder() {
    return myBuilder;
  }

  protected List<StreamChain> buildChains() {
    return ApplicationManager.getApplication().runReadAction((Computable<List<StreamChain>>)() -> {
      final PsiElement elementAtCaret = configureAndGetElementAtCaret();
      assertNotNull(elementAtCaret);
      final StreamChainBuilder builder = getChainBuilder();
      assertTrue(builder.isChainExists(elementAtCaret));
      return builder.build(elementAtCaret);
    });
  }

  protected String getFileExtension() {
    return ".java";
  }

  @NotNull
  protected abstract String getRelativeTestPath();
}
