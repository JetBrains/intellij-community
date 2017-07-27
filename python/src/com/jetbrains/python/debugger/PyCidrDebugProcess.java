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
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.memory.Address;
import com.jetbrains.cidr.execution.debugger.memory.AddressRange;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.event.HyperlinkListener;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PyCidrDebugProcess extends XDebugProcess {
  private final static Logger LOG = Logger.getInstance(PyCidrDebugProcess.class);

  private final MixedCidrDebugProcess myCidrProcess;
  private final PyDebugProcess myPyProcess;
  private final Collection<SharedLibInfo> mySharedLibInfos = new HashSet<>();

  private static Set<String> myDebuggableExternalLibs = new HashSet<>();
    //add("/usr/local/lib/python2.7/site-packages/ext.so");

  private final static String PY_FUNCTION_CALL_SYMBOL = "ceval.c:call_function";

  private final static String GET_FUNCTION_ARG_COMMAND = "(*((*pp_stack) - ((oparg & 0xff) + 2 * ((oparg>>8) & 0xff)) - 1))";
  private final static String GET_FUNCTION_POINTER_COMMAND =
    String.format("(((PyCFunctionObject *)%s) -> m_ml -> ml_meth)", GET_FUNCTION_ARG_COMMAND);
  private final static String IS_PYCFUNCTION_COMMAND =
    String.format("((((PyObject*)(%s))->ob_type) == &PyCFunction_Type)", GET_FUNCTION_ARG_COMMAND);

  public PyCidrDebugProcess(@NotNull XDebugSession session,
                            @NotNull CidrDebugProcess cidrDebugProcess,
                            @NotNull PyDebugProcess pyDebugProcess,
                            @NotNull String debuggableExternalLibs) {
    super(session);
    myCidrProcess = (MixedCidrDebugProcess)cidrDebugProcess;
    myPyProcess = pyDebugProcess;
    myDebuggableExternalLibs.addAll(Arrays.asList(debuggableExternalLibs.split(":")));
  }

  private void handleException(Throwable e) {
    LOG.error(e);
  }

  private static Location getActiveLocation(XSuspendContext context) {
    if (context instanceof PySuspendContext) {
      return Location.PY;
    } else {
      // CidrSuspendContext has private access in CidrDebugProcess
      // but we won't get anything else here
      return Location.CIDR;
    }
  }

  private XDebugProcess getActiveProcess(XSuspendContext context) {
    if (getActiveLocation(context) == Location.PY) {
      return myPyProcess;
    } else {
      return myCidrProcess;
    }
  }

  private void updateLibInfo(String[] info) {
    List<SharedLibInfo> infos = Arrays.stream(info)
      .map(line -> {
        String[] tokens = Arrays.stream(line.split(" "))
          .filter(s -> !s.isEmpty())
          .toArray(size -> new String[size]);
        String addressFrom = tokens[0];
        String addressTo = tokens[1];
        String fileName = tokens[tokens.length - 1];
        Predicate<String> isValidAddress = address -> address.matches("(0[xX])?0*[0-9a-fA-F]{1,16}");
        if (!myDebuggableExternalLibs.contains(fileName)
            || !isValidAddress.test(addressFrom) || !isValidAddress.test(addressTo)) {
          return null;
        }
        AddressRange range = new AddressRange(Address.parseHexString(addressFrom), Address.parseHexString(addressTo));
        return new SharedLibInfo(range, fileName);
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    mySharedLibInfos.addAll(infos);
    LOG.debug("updated lib info, currently loaded user's libs:");
    mySharedLibInfos.forEach(libInfo -> LOG.debug(libInfo.toString()));
  }

  private String getSdkHome() {
    XDebugSessionImpl session = (XDebugSessionImpl)getSession();
    ExecutionEnvironment environment = session.getExecutionEnvironment();
    if (environment == null) {
      return "<unknown>";
    }
    PythonRunConfiguration configuration = (PythonRunConfiguration)environment.getRunProfile();
    return configuration.getSdkHome();
  }

  private void addUserCodeBreakpoint(Runnable resumer) {
    LOG.debug("addUserCodeBreakpoint");
    Promise<String> evaluationPromise = myCidrProcess.evaluate(GET_FUNCTION_POINTER_COMMAND);
    evaluationPromise.rejected(this::handleException);
    Promise<Void> breakpointPromise = evaluationPromise
      .thenAsync(address -> myCidrProcess.addAddressBreakpointWithCallback(address, null, null));
    breakpointPromise.rejected(this::handleException);
    breakpointPromise.then(v -> {
      resumer.run();
      return null;
    });
  }

  private String getUserCodeDetectingCondition() {
    String isInRangeCondition = mySharedLibInfos.stream()
      .map(info ->
             GET_FUNCTION_POINTER_COMMAND + " >= " + info.getRange().getStart() +
             " && " +
             GET_FUNCTION_POINTER_COMMAND + " <= " + info.getRange().getEndInclusive())
      .collect(Collectors.joining(" || "));
    return IS_PYCFUNCTION_COMMAND + " && (" + isInRangeCondition + ")";
  }

  private Promise<Void> addJumpDetectingBreakpoint() {
    String condition = getUserCodeDetectingCondition();
    LOG.debug("Setting breakpoint with condition " + condition);
    Promise<Void> promise = myCidrProcess.addSymbolicBreakpointWithCallback(PY_FUNCTION_CALL_SYMBOL,
                                                                            condition,
                                                                            this::addUserCodeBreakpoint);
    promise.rejected(this::handleException);
    return promise;
  }

  // Todo: to merge cidr and py-editorProviders
  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myCidrProcess.getEditorsProvider();
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return ArrayUtil.mergeArrays(myCidrProcess.getBreakpointHandlers(), myPyProcess.getBreakpointHandlers());
  }

  public void sessionInitialized() {
    myPyProcess.sessionInitialized();
    myCidrProcess.sessionInitialized();
  }

  public void startPausing() {
    //myPyProcess.startPausing();
    myCidrProcess.startPausing();
  }

  public void startStepOver(@Nullable XSuspendContext context) {
    getActiveProcess(context).startStepOver(context);
  }

  public void startForceStepInto(@Nullable XSuspendContext context) {
    getActiveProcess(context).startForceStepInto(context);
  }

  public void startStepInto(@Nullable XSuspendContext context) {
    if (getActiveLocation(context) == Location.PY) {
      Promise<String[]> libsPromise = myCidrProcess.getLoadedLibsInfo();
      libsPromise.rejected(this::handleException);
      Promise<Void> breakpointsPromise = libsPromise.thenAsync(info -> {
        updateLibInfo(info);
        return addJumpDetectingBreakpoint();
      });

      breakpointsPromise.rejected(this::handleException);
      breakpointsPromise.then(v -> {
        doStartStepInto(context);
        return null;
      });
    } else {
      getActiveProcess(context).startStepInto(context);
    }
  }

  private void doStartStepInto(@Nullable XSuspendContext context) {
    LOG.debug("doStartStepInto");
    myPyProcess.startStepInto(context);
  }

  public void startStepOut(@Nullable XSuspendContext context) {
    getActiveProcess(context).startStepOut(context);
  }

  @Nullable
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return myCidrProcess.getSmartStepIntoHandler();
  }

  public void stop() {
    myCidrProcess.stop();
  //  myPyProcess.stop();
  }

  public void resume(@Nullable XSuspendContext context) {
    getActiveProcess(context).resume(context);
  }

  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    getActiveProcess(context).runToPosition(position, context);
  }

  public boolean checkCanPerformCommands() {
    return myCidrProcess.checkCanPerformCommands() && myPyProcess.checkCanPerformCommands();
  }

  public boolean checkCanInitBreakpoints() {
    return myCidrProcess.checkCanInitBreakpoints() && myPyProcess.checkCanInitBreakpoints();
  }

  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return myCidrProcess.getProcessHandler();
  }

  @NotNull
  public ExecutionConsole createConsole() {
    return myCidrProcess.createConsole();
    //return TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject()).getConsole();
  }

  @Nullable
  public XValueMarkerProvider<?,?> createValueMarkerProvider() {
    return myCidrProcess.createValueMarkerProvider();
  }

  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar, @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
    myPyProcess.registerAdditionalActions(leftToolbar, topToolbar, settings);
  }

  public String getCurrentStateMessage() {
    //return mySession.isStopped() ? XDebuggerBundle.message("debugger.state.message.disconnected") : XDebuggerBundle.message("debugger.state.message.connected");
    return myPyProcess.getCurrentStateMessage();
  }

  @Nullable
  public HyperlinkListener getCurrentStateHyperlinkListener() {
    return myPyProcess.getCurrentStateHyperlinkListener();
  }

  @NotNull
  public XDebugTabLayouter createTabLayouter() {
    return myCidrProcess.createTabLayouter();
  }

  public boolean isValuesCustomSorted() {
    return myPyProcess.isValuesCustomSorted();
  }

  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    XStackFrame frame = getSession().getCurrentStackFrame();
    return frame == null ? null : frame.getEvaluator();
  }

  public boolean isLibraryFrameFilterSupported() {
    return false;
  }

  private enum Location {
    CIDR, PY
  }

  private static class SharedLibInfo {
    @NotNull
    private final AddressRange myRange;
    @NotNull
    private final String myPath;

    SharedLibInfo(@NotNull AddressRange range, @NotNull String path) {
      myRange = range;
      myPath = path;
    }

    @NotNull
    AddressRange getRange() {
      return myRange;
    }

    @NotNull
    String getPath() {
      return myPath;
    }

    @Override
    public String toString() {
      return myRange + ", " + myPath;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof SharedLibInfo && equals((SharedLibInfo) other);
    }

    private boolean equals(SharedLibInfo other) {
      return Objects.equals(myRange, other.myRange) && Objects.equals(myPath, other.myPath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRange, myPath);
    }
  }
}
