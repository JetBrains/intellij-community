// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.statistics.Engine;
import com.intellij.debugger.statistics.StatisticsStorage;
import com.intellij.debugger.statistics.SteppingAction;
import com.intellij.xdebugger.XSourcePosition;
import com.sun.jdi.Location;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.base.util.SafeUtilKt;
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtil;

public final class DebuggerSteppingHelper {
    public static DebugProcessImpl.ResumeCommand createStepOverCommand(
            SuspendContextImpl suspendContext,
            boolean ignoreBreakpoints,
            SourcePosition sourcePosition
    ) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();

        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
                Location location = frameProxy == null ? null : SafeUtilKt.safeLocation(frameProxy);

                if (location != null) {
                    try {
                        KotlinSteppingCommandProviderKt
                                .getStepOverAction(location, suspendContext, frameProxy)
                                .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                                .contextAction(suspendContext);
                        return;
                    } catch (Exception ignored) {
                    }
                }

                debugProcess.createStepOutCommand(suspendContext).contextAction(suspendContext);
            }
        };
    }

    public static DebugProcessImpl.StepOverCommand createStepOverCommandForSuspendSwitch(SuspendContextImpl suspendContext) {
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
                                if (frameProxy != null && DebuggerUtil.isOnSuspensionPoint(frameProxy)) {
                                    return StepRequest.STEP_OVER;
                                }

                                return super.getNextStepDepth(context);
                            }
                        };
                hint.setIgnoreFilters(suspendContext.getDebugProcess().getSession().shouldIgnoreSteppingFilters());
                return hint;
            }

            @Override
            public Object createCommandToken() {
                return StatisticsStorage.createSteppingToken(SteppingAction.STEP_OVER, Engine.KOTLIN);
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new ResumeCommand(suspendContext) {
            @Override
            public void contextAction(@NotNull SuspendContextImpl suspendContext) {
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

                debugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints).contextAction(suspendContext);
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
            public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                try {
                    new KotlinStepAction.KotlinStepInto(methodFilter)
                            .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                            .contextAction(suspendContext);
                } catch (Exception e) {
                    debugProcess.createStepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter).contextAction(suspendContext);
                }
            }
        };
    }

    public static DebugProcessImpl.ResumeCommand createRunToCursorCommand(
            @NotNull SuspendContextImpl suspendContext,
            @NotNull XSourcePosition position,
            boolean ignoreBreakpoints
    ) {
        DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
        return debugProcess.new RunToCursorCommand(suspendContext, position, ignoreBreakpoints) {
            @Override
            public @Nullable LightOrRealThreadInfo getThreadFilterFromContext(@NotNull SuspendContextImpl suspendContext) {
                if (myContextThread != null) {
                    LightOrRealThreadInfo result = CoroutineJobInfo.extractJobInfo(suspendContext);
                    if (result != null) {
                        return result;
                    }
                }
                return super.getThreadFilterFromContext(suspendContext);
            }
        };
    }
}
