// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerCoreBundle;

public class KotlinStepActionFactory {
    private final static Logger LOG = Logger.getInstance(KotlinStepActionFactory.class);

    public static DebugProcessImpl.StepOverCommand createStepOverCommand(
            DebugProcessImpl debugProcess,
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            KotlinMethodFilter methodFilter
    ) {
        return debugProcess.new StepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, StepRequest.STEP_LINE) {
            @Override
            protected @NotNull String getStatusText() {
                return KotlinDebuggerCoreBundle.message("stepping.over.inline");
            }

            @Override
            protected @NotNull RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread) {
                KotlinStepOverRequestHint hint = new KotlinStepOverRequestHint(stepThread, suspendContext, methodFilter);
                hint.setResetIgnoreFilters(!debugProcess.getSession().shouldIgnoreSteppingFilters());
                try {
                    debugProcess.getSession().setIgnoreStepFiltersFlag(stepThread.frameCount());
                } catch (EvaluateException e) {
                    LOG.info(e);
                }
                return hint;
            }
        };
    }
}
