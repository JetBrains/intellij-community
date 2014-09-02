package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;


/**
 * Run a custom bit of Python in the context of the specified debug target.
 * <p>
 * This command takes a variable or expression (expressed as an {@link PyVariableLocator#getPyDBLocation()} style
 * location) and passes it to the function provided in the constructor. The constructor also takes either a code
 * snippet that should define the function, or a file to execfile that should define the function.
 * <p>
 * Once created, the command should be posted to the target with {@link AbstractDebugTarget#postCommand(AbstractDebuggerCommand)}.
 * Optionally, the function run on the target can return a string for further processing. In this case the command's
 * {@link #setCompletionListener(ICommandResponseListener)} should be set and on completion, {@link #getResponsePayload()}
 * can be used to obtain the returned value.
 * <p>
 * For an example, see {@link PrettyPrintCommandHandler}
 */
public class RunCustomOperationCommand<T> extends AbstractCommand<T> {
  private static final Logger LOG = Logger.getInstance(RunCustomOperationCommand.class);

  private String encodedCodeOrFile;
  private String operationFnName;
  private PyVariableLocator locator;
  private String style;
  private String responsePayload;

  private RunCustomOperationCommand(RemoteDebugger target, PyVariableLocator locator,
                                    String style, String codeOrFile, String operationFnName) {
    super(target, CMD_RUN_CUSTOM_OPERATION);

    this.locator = locator;
    this.style = style;
    this.encodedCodeOrFile = encode(codeOrFile);
    this.operationFnName = operationFnName;
  }

  /**
   * Create a new command to run with the function defined in a string.
   *
   * @param target Debug Target to run on
   * @param locator Location of variable or expression.
   * @param operationSource Definition of the function to be run (this code is "exec"ed by the target)
   * @param operationFnName Function to call, must be defined by operationSource
   */
  public RunCustomOperationCommand(RemoteDebugger target, PyVariableLocator locator,
                                   String operationSource, String operationFnName) {
    this(target, locator, "EXEC", operationSource, operationFnName);
  }

  /**
   * Create a new command to run with the function defined in a file.
   *
   * @param target Debug Target to run on
   * @param locator Location of variable or expression.
   * @param operationPyFile Definition of the function to be run (this file is "execfile"d by the target)
   * @param operationFnName Function to call, must be defined by operationSource
   */
  public RunCustomOperationCommand(RemoteDebugger target, PyVariableLocator locator,
                                   File operationPyFile, String operationFnName) {
    this(target, locator, "EXECFILE", operationPyFile.toString(), operationFnName);
  }


  @Override
  protected void buildPayload(Payload payload) {
    payload.add(locator.getPyDBLocation() + "||" + style).add(encodedCodeOrFile).add(operationFnName);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  /**
   * Return the response received from the custom command
   * @return the response or <code>null</code> if an error or no response has been received.
   */
  public String getResponsePayload() {
    return responsePayload;
  }

  private static String encode(String in) {
    try {
      return URLEncoder.encode(in, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.error("Unreachable? UTF-8 is always supported.", e);
      return "";
    }
  }

  protected static String decode(String in) {
    try {
      return URLDecoder.decode(in, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.error("Unreachable? UTF-8 is always supported.", e);
      return "";
    }
  }

}

