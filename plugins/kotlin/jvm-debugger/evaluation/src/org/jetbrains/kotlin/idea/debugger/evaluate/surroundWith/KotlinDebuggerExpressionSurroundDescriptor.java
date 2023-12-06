// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
