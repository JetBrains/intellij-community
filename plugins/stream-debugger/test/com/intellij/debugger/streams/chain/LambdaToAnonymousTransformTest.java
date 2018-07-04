// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupport;
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl;
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder;
import com.intellij.debugger.streams.psi.impl.PackageChainDetector;
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory;
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class LambdaToAnonymousTransformTest extends LightCodeInsightTestCase {
  public void testPsiFileException() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      configureFromFileText("Main.java", "import java.util.stream.Stream;\n" +
                                         "\n" +
                                         "public class Main {\n" +
                                         "  public static void main(String[] args) {\n" +
                                         "    <caret>Stream.of(1,2,3).map(x -> x * x ).filter(x -> x % 2 == 1).toArray();\n" +
                                         "  }\n" +
                                         "}\n", true);
      final PsiFile file = getFile();
      final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
      final PsiElement elementAtCaret = DebuggerUtilsEx.findElementAt(file, offset);
      PackageChainDetector chainDetector = PackageChainDetector.Companion.forJavaStreams("java.util.stream");
      final JavaStreamChainBuilder builder = new JavaStreamChainBuilder(new JavaChainTransformerImpl(), chainDetector);
      final List<StreamChain> chains = builder.build(elementAtCaret);
      assertEquals(1, chains.size());
      DslImpl javaDsl = new DslImpl(new JavaStatementFactory());
      StandardLibrarySupport librarySupport = new StandardLibrarySupport();
      final JavaTraceExpressionBuilder expressionBuilder = new JavaTraceExpressionBuilder(getProject(), librarySupport
        .createHandlerFactory(javaDsl), javaDsl);
      expressionBuilder.createTraceExpression(chains.get(0));
    });
  }
}
