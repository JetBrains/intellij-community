// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.streams.core.trace.dsl.impl.DslImpl;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupport;
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl;
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder;
import com.intellij.debugger.streams.psi.impl.PackageChainDetector;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory;
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class LambdaToAnonymousTransformTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture();

  @Test
  public void testPsiFileException() {
    final CodeInsightTestFixture fixture = builderFixture.get().getCodeInsight();
    fixture.configureByText("Main.java", """
      import java.util.stream.Stream;

      public class Main {
        public static void main(String[] args) {
          <caret>Stream.of(1,2,3).map(x -> x * x ).filter(x -> x % 2 == 1).toArray();
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiFile file = fixture.getFile();
      final int offset = fixture.getEditor().getCaretModel().getCurrentCaret().getOffset();
      final PsiElement elementAtCaret = DebuggerUtilsEx.findElementAt(file, offset);
      PackageChainDetector chainDetector = PackageChainDetector.Companion.forJavaStreams("java.util.stream");
      final JavaStreamChainBuilder builder = new JavaStreamChainBuilder(new JavaChainTransformerImpl(), chainDetector);
      final List<StreamChain> chains = builder.build(elementAtCaret);
      assertEquals(1, chains.size());
      DslImpl javaDsl = new DslImpl(new JavaStatementFactory());
      StandardLibrarySupport librarySupport = new StandardLibrarySupport();
      final JavaTraceExpressionBuilder expressionBuilder = new JavaTraceExpressionBuilder(fixture.getProject(), librarySupport
        .createHandlerFactory(javaDsl), javaDsl);
      expressionBuilder.createTraceExpression(chains.getFirst());
    }));
  }
}
