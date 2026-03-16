// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TextResult;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyVerSpecificSuperSignatureMacro extends Macro {
  @Override
  public String getName() {
    return "pyVerSpecificSuperSignature";
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    if (context.getPsiElementAtStartOffset() != null) {
      if (LanguageLevel.forElement(context.getPsiElementAtStartOffset()).isPython2()) {
        final Result classNameResult = new PyClassNameMacro().calculateResult(new Expression[]{}, context);
        if (classNameResult != null) {
          final String className = classNameResult.toString();
          final String self = "self";
          return new TextResult(className + ", " + self);
        }
      }
    }
    return new TextResult("");
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof PythonTemplateContextType;
  }
}
