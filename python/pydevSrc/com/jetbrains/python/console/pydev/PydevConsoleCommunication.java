package com.jetbrains.python.console.pydev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.util.*;

/**
 * Communication with Xml-rpc with the client.
 *
 * @author Fabio
 */
public class PydevConsoleCommunication implements IScriptConsoleCommunication, XmlRpcHandler, ConsoleCommunication {

  /**
   * XML-RPC client for sending messages to the server.
   */
  private IPydevXmlRpcClient client;

  /**
   * Responsible for getting the stdout of the process.
   */
  private final ThreadStreamReader stdOutReader;

  /**
   * Responsible for getting the stderr of the process.
   */
  private final ThreadStreamReader stdErrReader;

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  private WebServer webServer;

  private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class.getName());
  private final Project myProject;
  public static final int MAX_ATTEMPTS = 3;
  public static final long TIMEOUT = (long)(10e9);

  /**
   * Initializes the xml-rpc communication.
   *
   * @param port    the port where the communication should happen.
   * @param process this is the process that was spawned (server for the XML-RPC)
   * @throws MalformedURLException
   */
  public PydevConsoleCommunication(Project project, int port, Process process, int clientPort) throws Exception {
    myProject = project;
    stdOutReader = new ThreadStreamReader(process.getInputStream());
    stdErrReader = new ThreadStreamReader(process.getErrorStream());
    stdOutReader.start();
    stdErrReader.start();

    //start the server that'll handle input requests
    webServer = new WebServer(clientPort);
    webServer.addHandler("$default", this);
    this.webServer.start();

    IPydevXmlRpcClient client = new PydevXmlRpcClient(process, stdErrReader, stdOutReader, port);
    this.client = client;
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
   * Signals that the next command added should be sent as an input to the server.
   */
  public volatile boolean waitingForInput;

  /**
   * Input that should be sent to the server (waiting for raw_input)
   */
  private volatile String inputReceived;

  /**
   * Response that should be sent back to the shell.
   */
  private volatile InterpreterResponse nextResponse;

  /**
   * Helper to keep on busy loop.
   */
  private volatile Object lock = new Object();

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


  /**
   * Called when the server is requesting some input from this class.
   */
  public Object execute(String method, Vector params) throws Exception {
    waitingForInput = true;
    inputReceived = null;
    boolean needInput = true;

    //let the busy loop from execInterpreter free and enter a busy loop
    //in this function until execInterpreter gives us an input
    nextResponse = new InterpreterResponse(stdOutReader.getAndClearContents(),
                                           stdErrReader.getAndClearContents(), false, needInput);

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
  private Pair<String, Boolean> exec(final String command) throws XmlRpcException {
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
    boolean more;

    String errorContents = null;
    if (object instanceof Boolean) {
      more = (Boolean)object;
    }
    else {
      String str = object.toString();

      String lower = str.toLowerCase();
      if (lower.equals("true") || lower.equals("1")) {
        more = true;
      }
      else if (lower.equals("false") || lower.equals("0")) {
        more = false;
      }
      else {
        more = false;
        errorContents = str;
      }
    }
    return new Pair<String, Boolean>(errorContents, more);
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

              String refusedConnPattern = "Failed to read servers response";  // Was "refused", but it didn't
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
                    executed = new Pair<String, Boolean>(stdErrReader.getAndClearContents(), executed.second);
                    continue;
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

            String errorContents = executed.first;
            boolean more = executed.second;

            if (errorContents == null) {
              errorContents = stdErrReader.getAndClearContents();
            }
            nextResponse = new InterpreterResponse(stdOutReader.getAndClearContents(), errorContents, more, needInput);
          }
          catch (Exception e) {
            nextResponse = new InterpreterResponse("", "Exception while pushing line to console:" + e.getMessage(), false, needInput);
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
              nextResponse = new InterpreterResponse("", "Canceled", false, false);
            }

            final long time = System.nanoTime() - startTime;
            progressIndicator.setFraction(((double)time) / TIMEOUT);
            if (time > TIMEOUT) {
              LOG.debug("Timeout exceeded");
              nextResponse = new InterpreterResponse("", "Timeout exceeded", false, false);
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

  /**
   * @return completions from the client
   */
  public List<PydevCompletionVariant> getCompletions(final String prefix) throws Exception {
    if (waitingForInput) {
      return Collections.emptyList();
    }
    final Object fromServer = client.execute("getCompletions", new Object[]{prefix});

    final List<PydevCompletionVariant> ret = decodeCompletions(fromServer);

    return ret;
  }

  public static List<PydevCompletionVariant> decodeCompletions(Object fromServer) {
    final List<PydevCompletionVariant> ret = new ArrayList<PydevCompletionVariant>();

    List complList = objectToList(fromServer);

    for (Object o : complList) {
      List comp = objectToList(o);

      //name, doc, args, type
      final int type = extractInt(comp.get(3));
      final String args = AbstractPyCodeCompletion.getArgs((String)comp.get(2), type,
                                                           AbstractPyCodeCompletion.LOOKING_FOR_INSTANCED_VARIABLE);

      ret.add(new PydevCompletionVariant((String)comp.get(0), (String)comp.get(1), args, type));
    }
    return ret;
  }

  private static List objectToList(Object object) {
    List list;
    if (object instanceof Collection) {
      list = new ArrayList((Collection)object);
    }
    else if (object instanceof Object[]) {
      list = Arrays.asList((Object[])object);
    }
    else {
      throw new IllegalStateException("cant handle type of " + object);
    }
    return list;
  }


  /**
   * Extracts an int from an object
   *
   * @param objToGetInt the object that should be gotten as an int
   * @return int with the int the object represents
   */
  private static int extractInt(Object objToGetInt) {
    if (objToGetInt instanceof Integer) {
      return (Integer)objToGetInt;
    }
    return Integer.parseInt(objToGetInt.toString());
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
}
