package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface UserExpressionDescriptor extends ValueDescriptor {
  public void setContext(EvaluationContextImpl evaluationContext);
}
