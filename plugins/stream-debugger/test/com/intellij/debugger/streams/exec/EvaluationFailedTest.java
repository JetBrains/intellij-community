// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationFailedTest extends FailEvaluationTestCase {
  public void testEvaluationExceptionDetected() {
    doTest(true);
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
