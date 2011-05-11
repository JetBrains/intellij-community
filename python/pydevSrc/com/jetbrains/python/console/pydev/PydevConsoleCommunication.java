package com.jetbrains.python.console.pydev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.debugger.PydevXmlUtils;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * Communication with Xml-rpc with the client.
 *
 * @author Fabio
 */
public class PydevConsoleCommunication extends AbstractConsoleCommunication implements IScriptConsoleCommunication, XmlRpcHandler {

  /**
   * XML-RPC client for sending messages to the server.
   */
  private IPydevXmlRpcClient client;

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  private WebServer webServer;

  private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class.getName());

  /**
   * Input that should be sent to the server (waiting for raw_input)
   */
  protected volatile String inputReceived;
  /**
   * Response that should be sent back to the shell.
   */
  protected volatile InterpreterResponse nextResponse;
  /**
   * Helper to keep on busy loop.
   */
  private volatile Object lock2 = new Object();
  /**
   * Keeps a flag indicating that we were able to communicate successfully with the shell at least once
   * (if we haven't we may retry more than once the first time, as jython can take a while to initialize
   * the communication)
   */
  private volatile boolean firstCommWorked = false;

  private boolean myExecuting;

  /**
   * Initializes the xml-rpc communication.
   *
   * @param port    the port where the communication should happen.
   * @param process this is the process that was spawned (server for the XML-RPC)
   * @throws MalformedURLException
   */
  public PydevConsoleCommunication(Project project, int port, Process process, int clientPort) throws Exception {
    super(project);

    //start the server that'll handle input requests
    webServer = new WebServer(clientPort);
    webServer.addHandler("$default", this);
    this.webServer.start();

    this.client = new PydevXmlRpcClient(process, port);
  }

  public boolean handshake() throws XmlRpcException {
    if (client != null) {
      Object ret = client.execute("handshake", new Object[]{});
      if (ret instanceof String) {
        String retVal = (String)ret;
        return "PyCharm".equals(retVal);
      }
    }
    return false;
  }

  /**
   * Stops the communication with the client (passes message for it to quit).
   */
  public void close() throws Exception {
    if (this.client != null) {
      new Task.Backgroundable(myProject, "Close console communication", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            PydevConsoleCommunication.this.client.execute("close", new Object[0]);
          }
          catch (Exception e) {
            //Ok, we can ignore this one on close.
          }
          PydevConsoleCommunication.this.client = null;
        }
      }.queue();
    }

    if (this.webServer != null) {
      this.webServer.shutdown();
      this.webServer = null;
    }
  }

  /**
   * Variables that control when we're expecting to give some input to the server or when we're
   * adding some line to be executed
   */

  /**
   * Helper to keep on busy loop.
   */
  private volatile Object lock = new Object();


  /**
   * Called when the server is requesting some input from this class.
   */
  public Object execute(String method, Vector params) throws Exception {
    if ("NotifyFinished".equals(method)) {
      return execNotifyFinished();
    }
    else if ("RequestInput".equals(method)) {
      return execRequestInput();
    }
    else {
      throw new NotImplementedException();
    }
  }

  private Object execNotifyFinished() {
    setExecuting(false);
    notifyFinished();
    return true;
  }

  private void setExecuting(boolean executing) {
    myExecuting = executing;
  }

  private Object execRequestInput() {
    waitingForInput = true;
    inputReceived = null;
    boolean needInput = true;

    //let the busy loop from execInterpreter free and enter a busy loop
    //in this function until execInterpreter gives us an input
    nextResponse = new InterpreterResponse(false, needInput);

    //busy loop until we have an input
    while (inputReceived == null) {
      synchronized (lock) {
        try {
          lock.wait(10);
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
    }
    return inputReceived;
  }

  /**
   * Executes the needed command
   *
   * @return a Pair with (null, more) or (error, false)
   * @throws XmlRpcException
   */
  protected Pair<String, Boolean> exec(final String command) throws XmlRpcException {
    setExecuting(true);
    Object execute = client.execute("addExec", new Object[]{command});

    Object object;
    if (execute instanceof Vector) {
      object = ((Vector)execute).get(0);
    }
    else if (execute.getClass().isArray()) {
      object = ((Object[])execute)[0];
    }
    else {
      object = execute;
    }
    if (object instanceof Boolean) {
      return new Pair<String, Boolean>(null, (Boolean)object);
    }
    else {
      return parseExecResponseString(object.toString());
    }
  }

  /**
   * @return completions from the client
   */
  @NotNull
  public List<PydevCompletionVariant> getCompletions(final String prefix) throws Exception {
    if (waitingForInput) {
      return Collections.emptyList();
    }
    final Object fromServer = client.execute("getCompletions", new Object[]{prefix});

    return PydevXmlUtils.decodeCompletions(fromServer);
  }


  /**
   * @return the description of the given attribute in the shell
   */
  public String getDescription(String text) throws Exception {
    if (waitingForInput) {
      return "Unable to get description: waiting for input.";
    }
    return client.execute("getDescription", new Object[]{text}).toString();
  }

  /**
   * Executes a given line in the interpreter.
   *
   * @param command the command to be executed in the client
   */
  public void execInterpreter(final String command, final ICallback<Object, InterpreterResponse> onResponseReceived) {
    nextResponse = null;
    if (waitingForInput) {
      inputReceived = command;
      waitingForInput = false;
      //the thread that we started in the last exec is still alive if we were waiting for an input.
    }
    else {
      //create a thread that'll keep locked until an answer is received from the server.
      new Task.Backgroundable(myProject, "REPL Communication", true) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          boolean needInput = false;
          try {

            Pair<String, Boolean> executed = null;

            //the 1st time we'll do a connection attempt, we can try to connect n times (until the 1st time the connection
            //is accepted) -- that's mostly because the server may take a while to get started.
            int commAttempts = 0;
            while (true) {
              if (indicator.isCanceled()) {
                return;
              }

              executed = exec(command);

              //executed.o1 is not null only if we had an error

              String refusedConnPattern = "Failed to read servers response";
              // Was "refused", but it didn't
              // work on non English system
              // (in Spanish localized systems
              // it is "rechazada")
              // This string always works,
              // because it is hard-coded in
              // the XML-RPC library)
              if (executed.first != null && executed.first.indexOf(refusedConnPattern) != -1) {
                if (firstCommWorked) {
                  break;
                }
                else {
                  if (commAttempts < MAX_ATTEMPTS) {
                    commAttempts += 1;
                    Thread.sleep(250);
                    executed = new Pair<String, Boolean>("", executed.second);
                  }
                  else {
                    break;
                  }
                }
              }
              else {
                break;
              }

              //unreachable code!! -- commented because eclipse will complain about it
              //throw new RuntimeException("Can never get here!");
            }

            firstCommWorked = true;

            boolean more = executed.second;

            nextResponse = new InterpreterResponse(more, needInput);
          }
          catch (Exception e) {
            nextResponse = new InterpreterResponse(false, needInput);
          }
        }
      }.queue();


      //busy loop waiting for the answer (or having the console die).
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          progressIndicator.setText("Waiting for REPL response with " + (int)(TIMEOUT / 10e8) + "s timeout");
          final long startTime = System.nanoTime();
          while (nextResponse == null) {
            if (progressIndicator.isCanceled()) {
              LOG.debug("Canceled");
              nextResponse = new InterpreterResponse(false, false);
            }

            final long time = System.nanoTime() - startTime;
            progressIndicator.setFraction(((double)time) / TIMEOUT);
            if (time > TIMEOUT) {
              LOG.debug("Timeout exceeded");
              nextResponse = new InterpreterResponse(false, false);
            }
            synchronized (lock2) {
              try {
                lock2.wait(20);
              }
              catch (InterruptedException e) {
                LOG.error(e);
              }
            }
          }
          onResponseReceived.call(nextResponse);
        }
      }, "Waiting for REPL response", true, myProject);
    }
  }

  @Override
  public void interrupt() {
    try {
      client.execute("interrupt", new Object[]{});
    }
    catch (XmlRpcException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isExecuting() {
    return myExecuting;
  }
}
