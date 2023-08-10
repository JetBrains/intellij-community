// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager;

public class KotlinPositionManagerFactory extends PositionManagerFactory {
    @Override
    public PositionManager createPositionManager(@NotNull DebugProcess process) {
        return new KotlinPositionManager(process);
    }
}
