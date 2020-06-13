// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.protocol.*;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import org.apache.thrift.TException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.jetbrains.python.console.PydevConsoleCommunicationUtil.*;

/**
 * Communication with Python console backend using Thrift services.
 *
 * @author Fabio
 */
public abstract class PydevConsoleCommunication extends AbstractConsoleCommunication implements PyFrameAccessor {
  private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class);

  protected volatile boolean keyboardInterruption;
  /**
   * Input that should be sent to the server (waiting for raw_input)
   */
  protected volatile String inputReceived;
  /**
   * Response that should be sent back to the shell.
   */
  @Nullable protected volatile InterpreterResponse nextResponse;

  private boolean myExecuting;
  private PythonDebugConsoleCommunication myDebugCommunication;
  private boolean myNeedsMore = false;
  /**
   * UI sends a lot of repeated requests for the same expression
   * Store result of the previous request to avoid sending the same command several times
   */
  @Nullable private Pair<String, String> myPrevNameToDescription = null;

  private int myFullValueSeq = 0;
  private final Map<Integer, List<PyFrameAccessor.PyAsyncValue<String>>> myCallbackHashMap = new ConcurrentHashMap<>();

  @Nullable private PythonConsoleView myConsoleView;
  private final List<PyFrameListener> myFrameListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Nullable private XCompositeNode myCurrentRootNode;

  /**
   * Initializes the bidirectional RPC communication.
   */
  public PydevConsoleCommunication(Project project) {
    super(project);
  }

  /**
   * Returns thread safe, Python Console process-aware and disposable
   * {@link PythonConsoleBackendService.Iface}. Requests to the returned
   * {@link PythonConsoleBackendService.Iface} will be processed sequentially.
   * If Python Console process is detected to be finished the current request
   * will be interrupted and {@link PyConsoleProcessFinishedException} is
   * thrown.
   *
   * @return thread safe and related Python Console process-aware
   * {@link PythonConsoleBackendService.Iface}
   * @throws CommunicationClosedException if transport is closed
   */
  @NotNull
  protected abstract PythonConsoleBackendServiceDisposable getPythonConsoleBackendClient();

  /**
   * Sends <i>handshake</i> message to Python Console backend. Returns
   * {@code true} if Python Console backend replies with <i>PyCharm</i> string.
   * Returns {@code false} if Python Console backend replies with unexpected
   * message or Python Console process is finished or Python Console is closed.
   *
   * @return whether <i>handshake</i> with Python Console backend succeeded
   * @throws RuntimeException if transport (protocol) error occurs
   */
  public boolean handshake() {
    if (!isCommunicationClosed()) {
      try {
        return "PyCharm".equals(getPythonConsoleBackendClient().handshake());
      }
      catch (CommunicationClosedException | PyConsoleProcessFinishedException e) {
        return false;
      }
      catch (TException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return false;
    }
  }

  /**
   * Stops the communication with the client (passes message for it to quit).
   */
  public void close() {
    PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
    myCallbackHashMap.clear();

    new Task.Backgroundable(myProject, PyBundle.message("console.close.console.communication"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          closeCommunication().get();
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
          // it could help us diagnose some intricate cases
          LOG.debug(e);
        }
      }
    }.queue();
  }

  /**
   * Stops the communication with the client (passes message for it to quit).
   *
   * @return {@link Future} that allows to wait for Python Console transport
   * thread(s) to finish its execution
   */
  @NotNull
  public Future<?> closeAsync() {
    PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
    myCallbackHashMap.clear();

    return closeCommunication();
  }

  /**
   * Closes the communication with Python Console backend gracefully. Returns
   * {@link Future} that allows to wait for communication resources
   * (corresponding {@link java.util.concurrent.ExecutorService} and threads)
   * to be finished.
   * <p>
   * The method is not expected to throw any exception as well as the returned
   * {@link Future}.
   *
   * @return {@link Future}
   */
  @NotNull
  protected abstract Future<?> closeCommunication();

  public abstract boolean isCommunicationClosed();

  /*
   * Variables that control when we're expecting to give some input to the server or when we're
   * adding some line to be executed
   */

  /**
   * Helper to keep on busy loop.
   */
  private final Object lock = new Object();

  private void execNotifyAboutMagic(List<String> commands, boolean isAutoMagic) {
    if (getConsoleFile() != null) {
      PythonConsoleData consoleData = PyConsoleUtil.getOrCreateIPythonData(getConsoleFile());
      consoleData.setIPythonAutomagic(isAutoMagic);
      consoleData.setIPythonMagicCommands(commands);
    }
  }

  private boolean execIPythonEditor(String path) {
    final VirtualFile file = StringUtil.isEmpty(path) ? null : LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null) {
      ApplicationManager.getApplication().invokeLater(() -> FileEditorManager.getInstance(myProject).openFile(file, true));

      return true;
    }

    return false;
  }

  private void execNotifyFinished(boolean more) {
    myNeedsMore = more;
    setExecuting(false);
    notifyCommandExecuted(more);
  }

  private void setExecuting(boolean executing) {
    myExecuting = executing;
  }

  private Object execRequestInput() throws KeyboardInterruptException {
    waitingForInput = true;
    inputReceived = null;
    keyboardInterruption = false;
    boolean needInput = true;

    //let the busy loop from execInterpreter free and enter a busy loop
    //in this function until execInterpreter gives us an input
    nextResponse = new InterpreterResponse(false, needInput);

    notifyInputRequested();

    //busy loop until we have an input
    while (inputReceived == null) {
      if (keyboardInterruption) {
        waitingForInput = false;

        throw new KeyboardInterruptException();
      }
      synchronized (lock) {
        try {
          lock.wait(10);
        }
        catch (InterruptedException e) {
          //pass
        }
      }
    }
    return inputReceived;
  }

  /**
   * Executes the needed command
   *
   * @param command
   * @return a Pair with (null, more) or (error, false)
   */
  protected Pair<String, Boolean> exec(final ConsoleCodeFragment command) throws PythonUnhandledException {
    setExecuting(true);

    boolean more;
    try {
      if (command.isSingleLine()) {
        more = getPythonConsoleBackendClient().execLine(command.getText());
      }
      else {
        more = getPythonConsoleBackendClient().execMultipleLines(command.getText());
      }
    }
    catch (PythonUnhandledException e) {
      setExecuting(false);
      throw e;
    }
    catch (TException e) {
      setExecuting(false);
      throw new RuntimeException(e);
    }

    if (more) {
      setExecuting(false);
    }

    return Pair.create(null, more);
  }

  private static String createRuntimeMessage(String taskName) {
    return PyBundle.message("console.getting.from.runtime", taskName);
  }

  /**
   * @return completions from the client
   */
  @Override
  @NotNull
  public List<PydevCompletionVariant> getCompletions(String text, String actTok) throws Exception {
    if (waitingForInput || isExecuting()) {
      return Collections.emptyList();
    }
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText(createRuntimeMessage(PyBundle.message("console.getting.completion")));
    return ApplicationUtil.runWithCheckCanceled(
      () ->
      {
        try {
          if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
            return myDebugCommunication.getCompletions(text, actTok);
          }
          else {
            List<CompletionOption> fromServer = getPythonConsoleBackendClient().getCompletions(text, actTok);
            return ContainerUtil.map(fromServer, option -> toPydevCompletionVariant(option));
          }
        }
        catch (PythonUnhandledException e) {
          LOG.warn("Completion error in Python Console: " + e.traceback);
          return Collections.emptyList();
        }
      },
      indicator);
  }

  @NotNull
  private static PydevCompletionVariant toPydevCompletionVariant(@NotNull CompletionOption option) {
    String args = String.join(" ", option.arguments);
    return new PydevCompletionVariant(option.name, option.documentation, args, option.type.getValue());
  }

  private <T> void executeBackgroundTaskSuppressException(Callable<T> task,
                                                          String userVisibleMessage,
                                                          String errorLogMessage) {
    try {
      executeBackgroundTask(task, false, userVisibleMessage, errorLogMessage);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Nullable
  private <T> T executeBackgroundTask(Callable<T> task, boolean waitForResult, String userVisibleMessage, String errorLogMessage)
    throws PyDebuggerException {
    final FutureResult<T> future = new FutureResult<>();
    new Task.Backgroundable(myProject, userVisibleMessage, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          T result = task.call();
          future.set(result);
        }
        catch (PythonUnhandledException e) {
          LOG.error(errorLogMessage + e.traceback);
          future.set(null);
        }
        catch (Exception e) {
          future.setException(e);
        }
      }
    }.queue();
    if (waitForResult) {
      try {
        return future.get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new PyDebuggerException(errorLogMessage, e);
      }
    }
    return null;
  }

  /**
   * @return the description of the given attribute in the shell
   */
  @Override
  public String getDescription(String text) throws Exception {
    if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
      return myDebugCommunication.getDescription(text);
    }
    if (waitingForInput) {
      return "Unable to get description: waiting for input.";
    }
    if (myPrevNameToDescription != null) {
      if (myPrevNameToDescription.first.equals(text)) return myPrevNameToDescription.second;
    }
    else {
      // add temporary value to avoid repeated requests for the same expression
      myPrevNameToDescription = Pair.create(text, "");
    }
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new PyDebuggerException("Documentation in Python Console shouldn't be called from Dispatch Thread!");
    }

    return executeBackgroundTask(
      () ->
      {
        final String resultDescription = getPythonConsoleBackendClient().getDescription(text);
        myPrevNameToDescription = Pair.create(text, resultDescription);
        return resultDescription;
      },
      true,
      createRuntimeMessage(PyBundle.message("console.getting.documentation")),
      "Error when Getting Description in Python Console: ");
  }

  /**
   * Executes a given line in the interpreter.
   *
   * @param command the command to be executed in the client
   */
  @Override
  public void execInterpreter(final ConsoleCodeFragment command, final Function<InterpreterResponse, Object> onResponseReceived) {
    if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
      myDebugCommunication.execInterpreter(command, onResponseReceived);
      return; //TODO: handle text input and other cases
    }
    nextResponse = null;
    if (waitingForInput && myConsoleView != null && myConsoleView.isInitialized()) {
      inputReceived = command.getText();
      waitingForInput = false;
      //the thread that we started in the last exec is still alive if we were waiting for an input.
    }
    else {
      //create a thread that'll keep locked until an answer is received from the server.
      new Task.Backgroundable(myProject, PyBundle.message("console.waiting.execution.result"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (indicator.isCanceled()) return;
            Pair<String, Boolean> executed = exec(command);
            nextResponse = new InterpreterResponse(executed.second, false);
          }
          catch (ProcessCanceledException e) {
            //ignore
          }
          catch (PythonUnhandledException e) {
            LOG.error("Error in execInterpreter():" + e.traceback);
            nextResponse = new InterpreterResponse(false, false);
          }
          catch (Exception e) {
            nextResponse = new InterpreterResponse(false, false);
          }
          finally {
            InterpreterResponse response = nextResponse;
            if (response != null && response.more) {
              myNeedsMore = true;
            }
            notifyCommandExecuted(true);
            onResponseReceived.fun(response);
          }
        }
      }.queue();
    }
  }

  @Override
  public void interrupt() {
    if (waitingForInput) {
      // we do not want to forcibly `interrupt()` the `requestInput()` on the
      // Python side otherwise the message queue to the IDE will be broken
      keyboardInterruption = true;
      return;
    }
    executeBackgroundTaskSuppressException(
      () ->
      {
        getPythonConsoleBackendClient().interrupt();
        return null;
      },
      PyBundle.message("console.interrupting.execution"),
      "");
  }

  @Override
  public boolean isExecuting() {
    return myExecuting;
  }

  @Override
  public boolean needsMore() {
    return myNeedsMore;
  }

  @Override
  public PyDebugValue evaluate(String expression, boolean execute, boolean doTrunc) throws PyDebuggerException {
    if (!isCommunicationClosed()) {
      return executeBackgroundTask(
        () -> {
          List<DebugValue> debugValues = getPythonConsoleBackendClient().evaluate(expression, doTrunc);
          return createPyDebugValue(debugValues.iterator().next(), this);
        },
        true,
        PyBundle.message("console.evaluating.expression.in.console"),
        "Error in evaluate():"
      );
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public XValueChildrenList loadFrame() throws PyDebuggerException {
    if (!isCommunicationClosed()) {
      return executeBackgroundTask(
        () -> {
          List<DebugValue> frame = getPythonConsoleBackendClient().getFrame();
          return parseVars(frame, null, this);
        },
        true,
        createRuntimeMessage(PyBundle.message("console.getting.frame.variables")),
        "Error in loadFrame():"
      );
    }
    else {
      return new XValueChildrenList();
    }
  }

  public synchronized int getNextFullValueSeq() {
    myFullValueSeq++;
    return myFullValueSeq;
  }

  @Override
  public void loadAsyncVariablesValues(@NotNull List<PyAsyncValue<String>> pyAsyncValues) {
    PyDebugValueExecutionService.getInstance(myProject).submitTask(this, () -> {
      try {
        List<String> evaluationExpressions = new ArrayList<>();
        for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
          evaluationExpressions.add(GetVariableCommand.composeName(asyncValue.getDebugValue()));
        }
        final int seq = getNextFullValueSeq();
        myCallbackHashMap.put(seq, pyAsyncValues);

        getPythonConsoleBackendClient().loadFullValue(seq, evaluationExpressions);

        // previously `loadFullValue()` might return `List<PyDebugValue>` but this is no longer true
      }
      catch (CommunicationClosedException | PyConsoleProcessFinishedException | TException e) {
        for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
          PyDebugValue value = asyncValue.getDebugValue();
          XValueNode node = value.getLastNode();
          if (node != null && !node.isObsolete()) {
            if (e.getMessage().startsWith("Timeout") || e.getMessage().startsWith("Console already exited")) {
              value.updateNodeValueAfterLoading(node, " ", "", PyVariableViewSettings.LOADING_TIMED_OUT);
            }
            else {
              LOG.error(e);
            }
          }
        }
      }
    });
  }

  @Override
  public XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException {
    if (!isCommunicationClosed()) {
      return executeBackgroundTask(
        () -> {
          final String name = var.getOffset() == 0 ? GetVariableCommand.composeName(var)
                                                   : var.getOffset() + "\t" + GetVariableCommand.composeName(var);
          List<DebugValue> ret = getPythonConsoleBackendClient().getVariable(name);
          return parseVars(ret, var, this);
        },
        true,
        createRuntimeMessage(PyBundle.message("console.getting.variable.value")),
        "Error in loadVariable():"
      );
    }
    else {
      return new XValueChildrenList();
    }
  }

  @Override
  public void setCurrentRootNode(@NotNull XCompositeNode node) {
    myCurrentRootNode = node;
  }

  @Override
  public boolean isSimplifiedView() {
    return PyDebuggerSettings.getInstance().isSimplifiedView();
  }

  @Override
  @Nullable
  public XCompositeNode getCurrentRootNode() {
    return myCurrentRootNode;
  }

  @Override
  public void changeVariable(PyDebugValue variable, String value) {
    if (!isCommunicationClosed()) {
      executeBackgroundTaskSuppressException(
        () -> {
          // NOTE: The actual change is being scheduled in the exec_queue in main thread
          // This method is async now
          getPythonConsoleBackendClient().changeVariable(variable.getEvaluationExpression(), value);
          return null;
        },
        PyBundle.message("console.changing.variable"),
        "Error in changeVariable():"
      );
    }
  }

  @Nullable
  @Override
  public PyReferrersLoader getReferrersLoader() {
    return null;
  }

  @Override
  public ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format)
    throws PyDebuggerException {
    if (!isCommunicationClosed()) {
      return executeBackgroundTask(
        () -> {
          GetArrayResponse ret = getPythonConsoleBackendClient().getArray(var.getName(), rowOffset, colOffset, rows, cols, format);
          return createArrayChunk(ret, this);
        },
        true,
        createRuntimeMessage(PyBundle.message("console.getting.array")),
        "Error in getArrayItems():"
      );
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForName(String name, String parentType) {
    return null;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForType(String type) {
    return null;
  }

  /**
   * Request that pydevconsole connect (with pydevd) to the specified port
   *
   * @param localPort port for pydevd to connect to.
   * @param dbgOpts   additional debugger options (that are normally passed via command line) to apply
   * @param extraEnvs
   * @throws Exception if connection fails
   */
  public void connectToDebugger(int localPort, @Nullable String debuggerHost, @NotNull Map<String, Boolean> dbgOpts, @NotNull Map<String, String> extraEnvs)
    throws Exception {
    if (waitingForInput) {
      throw new Exception("Can't connect debugger now, waiting for input");
    }
    executeBackgroundTask(
      () -> {
        // though `connectToDebugger` returns "connect complete" string, let us just ignore it
        getPythonConsoleBackendClient().connectToDebugger(localPort, debuggerHost, dbgOpts, extraEnvs);
        return null;
      },
      true,
      PyBundle.message("console.connecting.to.debugger"),
      "Error in connectToDebugger():"
    );
  }

  @Override
  public void notifyCommandExecuted(boolean more) {
    super.notifyCommandExecuted(more);
    for (PyFrameListener listener : myFrameListeners) {
      listener.frameChanged();
    }
  }

  public void setDebugCommunication(PythonDebugConsoleCommunication debugCommunication) {
    myDebugCommunication = debugCommunication;
  }

  public PythonDebugConsoleCommunication getDebugCommunication() {
    return myDebugCommunication;
  }

  public void setConsoleView(@Nullable PythonConsoleView consoleView) {
    myConsoleView = consoleView;
  }

  @Override
  public void showNumericContainer(@NotNull PyDebugValue value) {
    PyViewNumericContainerAction.showNumericViewer(myProject, value);
  }

  @Override
  public void addFrameListener(@NotNull PyFrameListener listener) {
    myFrameListeners.add(listener);
  }

  @NotNull
  protected final PythonConsoleFrontendService.Iface createPythonConsoleFrontendHandler() {
    return new PythonConsoleFrontendHandler();
  }

  private class PythonConsoleFrontendHandler implements PythonConsoleFrontendService.Iface {

    @Override
    public void notifyFinished(boolean needsMoreInput) {
      execNotifyFinished(needsMoreInput);
    }

    @Override
    public String requestInput(String path) throws KeyboardInterruptException {
      return (String)execRequestInput();
    }

    @Override
    public void notifyAboutMagic(List<String> commands, boolean isAutoMagic) {
      execNotifyAboutMagic(commands, isAutoMagic);
    }

    @Override
    public void showConsole() {
      if (myConsoleView != null) {
        myConsoleView.setConsoleEnabled(true);
      }
    }

    @Override
    public void returnFullValue(int requestSeq, List<DebugValue> response) {
      final List<PyAsyncValue<String>> values = myCallbackHashMap.remove(requestSeq);
      try {
        List<PyDebugValue> debugValues = ContainerUtil.map(response, value -> createPyDebugValue(value, PydevConsoleCommunication.this));
        for (int i = 0; i < debugValues.size(); ++i) {
          PyDebugValue resultValue = debugValues.get(i);
          values.get(i).getCallback().ok(resultValue.getValue());
        }
      }
      catch (Exception e) {
        if (values != null) {
          for (PyFrameAccessor.PyAsyncValue vars : values) {
            vars.getCallback().error(new PyDebuggerException(response.toString()));
          }
        }
      }
    }

    @Override
    public boolean IPythonEditor(String path, String line) {
      return execIPythonEditor(path);
    }
  }

  protected static class CommunicationClosedException extends RuntimeException {
  }
}
