/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.event.HyperlinkListener;
import java.net.ServerSocket;
import java.util.Arrays;

public class PyCidrDebugProcess extends XDebugProcess {
  private final CidrDebugProcess myCidrDebugProcess;
  private final PyDebugProcess myPyDebugProcess;

  public PyCidrDebugProcess(CidrDebugProcess cidrDebugProcess, PyDebugProcess pyDebugProcess, XDebugSession session) {
    super(session);
    myCidrDebugProcess = cidrDebugProcess;
    myPyDebugProcess = pyDebugProcess;
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myCidrDebugProcess.getEditorsProvider();
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return ContainerUtil.toArray(
      ContainerUtil.concat(
        Arrays.asList(myCidrDebugProcess.getBreakpointHandlers()),
        Arrays.asList(myPyDebugProcess.getBreakpointHandlers())
      ),
      size -> new XBreakpointHandler<?>[size]
      );
  }

  public void sessionInitialized() {
    myPyDebugProcess.sessionInitialized();
    myCidrDebugProcess.sessionInitialized();
  }

  public void startPausing() {
    //myPyDebugProcess.startPausing();
    myCidrDebugProcess.startPausing();
  }

  @Deprecated
  public void startStepOver() {
    myCidrDebugProcess.startStepOver();
  }

  public void startStepOver(@Nullable XSuspendContext context) {
    getActiveProcess(context).startStepOver(context);
  }

  @Deprecated
  public void startForceStepInto(){
    myCidrDebugProcess.startForceStepInto();
  }

  public void startForceStepInto(@Nullable XSuspendContext context) {
    getActiveProcess(context).startForceStepInto(context);
  }

  @Deprecated
  public void startStepInto() {
    myCidrDebugProcess.startStepInto();
  }

  public void startStepInto(@Nullable XSuspendContext context) {
    getActiveProcess(context).startStepInto(context);
  }

  @Deprecated
  public void startStepOut() {
    myCidrDebugProcess.startStepOut();
  }

  public void startStepOut(@Nullable XSuspendContext context) {
    getActiveProcess(context).startStepOut(context);
  }

  @Nullable
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return myCidrDebugProcess.getSmartStepIntoHandler();
  }

  public void stop() {
    myCidrDebugProcess.stop();
    myPyDebugProcess.stop();
  }

  @NotNull
  public Promise stopAsync() {
    return myCidrDebugProcess.stopAsync();
  }

  @Deprecated
  public void resume() {
    myCidrDebugProcess.resume();
  }

  public void resume(@Nullable XSuspendContext context) {
    getActiveProcess(context).resume(context);
  }

  private XDebugProcess getActiveProcess(XSuspendContext context) {
    if (context instanceof PySuspendContext) {
      return myPyDebugProcess;
    }
    return myCidrDebugProcess;
  }

  @Deprecated
  public void runToPosition(@NotNull XSourcePosition position) {
    myCidrDebugProcess.runToPosition(position);
  }

  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    getActiveProcess(context).runToPosition(position, context);
  }

  public boolean checkCanPerformCommands() {
    return myCidrDebugProcess.checkCanPerformCommands();
  }

  public boolean checkCanInitBreakpoints() {
    return myCidrDebugProcess.checkCanInitBreakpoints();
  }

  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return myCidrDebugProcess.getProcessHandler();
  }

  @NotNull
  public ExecutionConsole createConsole() {
    return myCidrDebugProcess.createConsole();
    //return TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject()).getConsole();
  }

  @Nullable
  public XValueMarkerProvider<?,?> createValueMarkerProvider() {
    return myCidrDebugProcess.createValueMarkerProvider();
  }

  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar, @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
    myPyDebugProcess.registerAdditionalActions(leftToolbar, topToolbar, settings);
  }

  public String getCurrentStateMessage() {
    //return mySession.isStopped() ? XDebuggerBundle.message("debugger.state.message.disconnected") : XDebuggerBundle.message("debugger.state.message.connected");
    return myPyDebugProcess.getCurrentStateMessage();
  }

  @Nullable
  public HyperlinkListener getCurrentStateHyperlinkListener() {
    return myPyDebugProcess.getCurrentStateHyperlinkListener();
  }

  @NotNull
  public XDebugTabLayouter createTabLayouter() {
    return myPyDebugProcess.createTabLayouter();
  }

  public boolean isValuesCustomSorted() {
    return myPyDebugProcess.isValuesCustomSorted();
  }

  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    return myPyDebugProcess.getEvaluator();
    //XStackFrame frame = getSession().getCurrentStackFrame();
    //return frame == null ? null : frame.getEvaluator();
  }

  public boolean isLibraryFrameFilterSupported() {
    return false;
  }
}
