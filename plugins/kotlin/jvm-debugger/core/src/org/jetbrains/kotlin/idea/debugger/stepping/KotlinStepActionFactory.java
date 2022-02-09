// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerCoreBundle;

public class KotlinStepActionFactory {
    private final static Logger LOG = Logger.getInstance(KotlinStepActionFactory.class);

    @NotNull
    public static DebugProcessImpl.StepOverCommand createKotlinStepOverCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            @NotNull KotlinMethodFilter methodFilter
    ) {
        return debugProcess.new StepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, StepRequest.STEP_LINE) {
            @Override
            protected @NotNull String getStatusText() {
                return KotlinDebuggerCoreBundle.message("stepping.over.inline");
            }

            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                KotlinStepOverRequestHint hint = new KotlinStepOverRequestHint(stepThread, suspendContext, methodFilter, parentHint);
                hint.setResetIgnoreFilters(!debugProcess.getSession().shouldIgnoreSteppingFilters());
                hint.setRestoreBreakpoints(ignoreBreakpoints);
                try {
                    debugProcess.getSession().setIgnoreStepFiltersFlag(stepThread.frameCount());
                } catch (EvaluateException e) {
                    LOG.info(e);
                }
                return hint;
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepIntoCommand createKotlinStepIntoCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            @Nullable MethodFilter methodFilter
    ) {
        return debugProcess.new StepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter, StepRequest.STEP_LINE) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                KotlinStepIntoRequestHint hint = new KotlinStepIntoRequestHint(stepThread, suspendContext, methodFilter, parentHint);
                hint.setResetIgnoreFilters(myMethodFilter != null && !debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepIntoCommand createStepIntoCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreFilters,
            @Nullable MethodFilter methodFilter,
            int stepSize
    ) {
        return debugProcess.new StepIntoCommand(suspendContext, ignoreFilters, methodFilter, stepSize) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                RequestHint hint = new KotlinRequestHint(stepThread, suspendContext, stepSize, StepRequest.STEP_INTO, methodFilter, parentHint);
                hint.setResetIgnoreFilters(myMethodFilter != null && !debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }
        };
    }

    @NotNull
    public static DebugProcessImpl.StepOutCommand createStepOutCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext
    ) {
        return debugProcess.new StepOutCommand(suspendContext, StepRequest.STEP_LINE) {
            @Override
            public @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                RequestHint hint = new KotlinRequestHint(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_OUT, null, parentHint);
                hint.setIgnoreFilters(debugProcess.getSession().shouldIgnoreSteppingFilters());
                return hint;
            }
        };
    }
}
