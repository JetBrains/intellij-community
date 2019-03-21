// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion.pathFilters;

import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.function.Predicate;

public interface PyPathFilter extends Predicate<PyStringLiteralExpression> {
}
