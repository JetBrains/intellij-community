// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger;

import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.jetbrains.javascript.debugger.JavaScriptDebugAware;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinLineBreakpointType;

public class KotlinJavaScriptDebugAware extends JavaScriptDebugAware {
    @Nullable
    @Override
    public Class<? extends XLineBreakpointType<?>> getBreakpointTypeClass() {
        return KotlinLineBreakpointType.class;
    }
}
