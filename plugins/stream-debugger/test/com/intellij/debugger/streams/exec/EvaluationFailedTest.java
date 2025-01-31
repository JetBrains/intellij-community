// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * @author Vitaliy.Bibaev
 */
@RunWith(JUnit4.class)
public class EvaluationFailedTest extends FailEvaluationTestCase {
  @Test
  public void testEvaluationExceptionDetected() {
    assumeFalse(Registry.is("debugger.evaluate.method.helper"));
    doTest(true, "EvaluationExceptionDetected");
  }

  @Test
  public void testEvaluationExceptionDetectedHelper() {
    assumeTrue(Registry.is("debugger.evaluate.method.helper"));
    doTest(true, "EvaluationExceptionDetected");
  }

  @Override
  protected TraceExpressionBuilder getExpressionBuilder() {
    final TraceExpressionBuilder builder = super.getExpressionBuilder();
    return chain -> "if(true) throw new RuntimeException(\"My exception message\");\n" + builder.createTraceExpression(chain);
  }

  @NotNull
  @Override
  protected String replaceAdditionalInOutput(@NotNull String str) {
    String output = super.replaceAdditionalInOutput(str);
    String generatedClassName = CompilingEvaluator.getGeneratedClassName();
    StringBuilder builder = new StringBuilder();
    for (String line : StringUtil.splitByLinesKeepSeparators(output)) {
      if (line.trim().startsWith("at ") && line.contains(generatedClassName)) {
        line = StringUtil.substringBefore(line, "at") + "at !GENERATED_EVALUATION_CLASS!" + StringUtil.substringAfterLast(line, ")");
      }
      builder.append(line);
    }
    return builder.toString();
  }
}
