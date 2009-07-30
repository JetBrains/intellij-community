package com.jetbrains.python.sdk;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 * @author dcheryasov
 * Date: Apr 24, 2008
 * Time: 1:19:47 PM
 */
public class SdkUtil {
  protected static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.SdkVersionUtil");

  private SdkUtil() {
    // explicitly none
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command) {
    return getProcessOutput(homePath, command, -1);
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * Waits for process for possibly limited duration.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @param timeout how many milliseconds to wait until the process terminates; non-positive means inifinity.
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null. If
   * the process timed out, exit code is ProcessCallInfo.TIMEOUT_CODE.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessOutput();
    }
    try {
      List<String> commands = new ArrayList<String>();
      if (SystemInfo.isWindows && StringUtil.endsWithIgnoreCase(command [0], ".bat")) {
        commands.add("cmd");
        commands.add("/c");
      }
      Collections.addAll(commands, command);
      Process process = Runtime.getRuntime().exec(commands.toArray(new String[commands.size()]), null, new File(homePath));
      CapturingProcessHandler processHandler = new CapturingProcessHandler(process);
      return processHandler.runProcess(timeout);
    }
    catch (IOException ex) {
      LOG.info(ex);
      return new ProcessOutput();
    }
  }

  /**
  * Finds the first match in a list os Strings.
  * @param lines list of lines, may be null.
  * @param regex pattern to match to.
  * @return pattern's first matched group, or entire matched string if pattern has no groups, or null.
  */
  @Nullable
  public static String getFirstMatch(List<String> lines, Pattern regex) {
    if (lines == null) return null;
    for (String s: lines) {
      Matcher m = regex.matcher(s);
      if (m.matches()) {
        if (m.groupCount() > 0)
          return m.group(1);
      }
      else return s;
    }
    return null;
  }

}
