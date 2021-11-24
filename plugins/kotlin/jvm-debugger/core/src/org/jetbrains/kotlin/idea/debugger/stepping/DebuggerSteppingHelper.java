// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.Location;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.DebuggerUtilKt;
import org.jetbrains.kotlin.idea.debugger.SafeUtilKt;

public class DebuggerSteppingHelper {
    public static DebugProcessImpl.ResumeCommand createStepOverCommand(
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            SourcePosition sourcePosition
    ) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();

        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction() {
                StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
                Location location = frameProxy == null ? null : SafeUtilKt.safeLocation(frameProxy);

                if (location != null) {
                    try {
                        KotlinSteppingCommandProviderKt
                                .getStepOverAction(location, sourcePosition, suspendContext, frameProxy)
                                .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                                .contextAction(suspendContext);
                        return;
                    } catch (Exception ignored) {
                    }
                }

                debugProcess.createStepOutCommand(suspendContext).contextAction();
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createStepOverCommandForSuspendSwitch(SuspendContextImpl suspendContext) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new StepOverCommand(suspendContext, false, null, StepRequest.STEP_MIN) {
            @NotNull
            @Override
            public RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
                RequestHint hint =
                        new RequestHint(stepThread, suspendContext, StepRequest.STEP_MIN, StepRequest.STEP_OVER, myMethodFilter, parentHint) {
                            @Override
                            public int getNextStepDepth(SuspendContextImpl context) {
                                StackFrameProxyImpl frameProxy = context.getFrameProxy();
                                if (frameProxy != null && DebuggerUtilKt.isOnSuspensionPoint(frameProxy)) {
                                    return StepRequest.STEP_OVER;
                                }

                                return super.getNextStepDepth(context);
                            }
                        };
                hint.setIgnoreFilters(suspendContext.getDebugProcess().getSession().shouldIgnoreSteppingFilters());
                return hint;
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction() {
                StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
                Location location = frameProxy == null ? null : SafeUtilKt.safeLocation(frameProxy);

                if (location != null) {
                    try {
                        KotlinSteppingCommandProviderKt
                                .getStepOutAction(location, frameProxy)
                                .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                                .contextAction(suspendContext);
                        return;
                    } catch (Exception ignored) {
                    }
                }

                debugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints).contextAction();
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createStepIntoCommand(
            @NotNull SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            @Nullable MethodFilter methodFilter
    ) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction() {
                try {
                    new KotlinStepAction.KotlinStepInto(methodFilter)
                            .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                            .contextAction(suspendContext);
                } catch (Exception e) {
                    debugProcess.createStepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter).contextAction();
                }
            }
        };
    }
}
