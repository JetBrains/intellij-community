// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.filters;

import com.intellij.execution.filters.ExceptionFilterFactory;
import com.intellij.execution.filters.Filter;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class KotlinExceptionFilterFactory implements ExceptionFilterFactory {
    @NotNull
    @Override
    public Filter create(@NotNull GlobalSearchScope searchScope) {
        return new KotlinExceptionFilter(searchScope);
    }
}
