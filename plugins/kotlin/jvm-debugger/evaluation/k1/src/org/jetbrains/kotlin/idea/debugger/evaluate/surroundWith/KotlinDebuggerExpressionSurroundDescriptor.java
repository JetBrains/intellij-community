// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurroundDescriptorBase;
import org.jetbrains.kotlin.idea.debugger.surroundWith.KotlinRuntimeTypeCastSurrounder;

public class KotlinDebuggerExpressionSurroundDescriptor extends KotlinExpressionSurroundDescriptorBase {

    private static final Surrounder[] SURROUNDERS = {
            new KotlinRuntimeTypeCastSurrounder()
    };

    @Override
    public Surrounder @NotNull [] getSurrounders() {
        return SURROUNDERS;
    }
}
