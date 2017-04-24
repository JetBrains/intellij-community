/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.chain;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.JdkManager;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilderImpl;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
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
  private final StreamChainBuilder myChainBuilder = new StreamChainBuilderImpl();

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
  protected StreamChainBuilder getChainBuilder() {
    return myChainBuilder;
  }

  protected List<StreamChain> buildChains() {
    final PsiElement elementAtCaret = configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    return getChainBuilder().build(elementAtCaret);
  }

  @NotNull
  protected abstract String getRelativeTestPath();
}
