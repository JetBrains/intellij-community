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

  private static Set<String> myFilesToIndex = new HashSet<String>() {{
    // this is a mock, will be replaced asap
    add("/usr/local/lib/python2.7/site-packages/ext.so");
  }};

  private final static String GET_FUNCTION_POINTER_COMMAND = "(((PyCFunctionObject *)func) -> m_ml -> ml_meth)";

  public PyCidrDebugProcess(@NotNull XDebugSession session,
                            @NotNull CidrDebugProcess cidrDebugProcess,
                            @NotNull PyDebugProcess pyDebugProcess) {
    super(session);
    myCidrProcess = (MixedCidrDebugProcess)cidrDebugProcess;
    myPyProcess = pyDebugProcess;
  }

  private void handleException(Exception e) {
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
        if (!myFilesToIndex.contains(fileName)
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
    myCidrProcess.evaluateAndThen(GET_FUNCTION_POINTER_COMMAND,
                                  address -> myCidrProcess.addAddressBreakpointWithCallbackAndThen(address, null, null, resumer, this::handleException),
                                  this::handleException);
  }

  private String getUserCodeDetectingCondition() {
    return mySharedLibInfos.stream().map(info ->
                                           GET_FUNCTION_POINTER_COMMAND + " >= " + info.getRange().getStart() +
                                           " && " +
                                           GET_FUNCTION_POINTER_COMMAND + " <= " + info.getRange().getEndInclusive())
      .collect(Collectors.joining(" || "));
  }

  private void addJumpDetectingBreakpointsAndThen(Runnable callback) {
    String condition = getUserCodeDetectingCondition();
    LOG.debug("Setting breakpoint with condition " + condition);
    myCidrProcess.addSymbolicBreakpointWithCallbackAndThen("PyCFunction_Call",
                                                           condition,
                                                           this::addUserCodeBreakpoint,
                                                           callback,
                                                           this::handleException);
  }

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
    myPyProcess.startPausing();
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
      myCidrProcess.getLoadedLibsInfoAndThen(info -> {
        updateLibInfo(info);
        addJumpDetectingBreakpointsAndThen(() -> doStartStepInto(context));
      }, this::handleException);
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
    myPyProcess.stop();
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
    return myPyProcess.createTabLayouter();
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
