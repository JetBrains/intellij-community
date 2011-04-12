package com.jetbrains.python.sdk;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    return getProcessOutput(homePath, command, null, timeout);
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * Waits for process for possibly limited duration.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @param addEnv items are prepended to same-named values of inherited process environment.
   * @param timeout how many milliseconds to wait until the process terminates; non-positive means inifinity.
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, @Nullable @NonNls String[] addEnv, final int timeout) {
    return getProcessOutput(homePath, command, addEnv, timeout, null);
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * Waits for process for possibly limited duration.
   *
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @param addEnv items are prepended to same-named values of inherited process environment.
   * @param timeout how many milliseconds to wait until the process terminates; non-positive means infinity.
   * @param stdin the data to write to the process standard input stream
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath,
                                               @NonNls String[] command,
                                               @Nullable @NonNls String[] addEnv,
                                               final int timeout,
                                               @Nullable byte[] stdin) {
    final ProcessOutput failure_output = new ProcessOutput();
    if (homePath == null || !new File(homePath).exists()) {
      return failure_output;
    }
    try {
      List<String> commands = new ArrayList<String>();
      if (SystemInfo.isWindows && StringUtil.endsWithIgnoreCase(command [0], ".bat")) {
        commands.add("cmd");
        commands.add("/c");
      }
      Collections.addAll(commands, command);
      String[] new_env = null;
      if (addEnv != null) {
        Map<String, String> env_map = new HashMap<String, String>(System.getenv());
        // turn additional ent into map
        Map<String, String> add_map = new HashMap<String, String>();
        for (String env_item : addEnv) {
          int pos = env_item.indexOf('=');
          if (pos > 0) {
            String key = env_item.substring(0, pos);
            String value = env_item.substring(pos+1, env_item.length());
            add_map.put(key, value);
          }
          else LOG.warn(String.format("Invalid env value: '%s'", env_item));
        }
        // fuse old and new
        for (Map.Entry<String, String> entry : add_map.entrySet()) {
          final String key = entry.getKey();
          final String value = entry.getValue();
          final String old_value = env_map.get(key);
          if (old_value != null) env_map.put(key, value + old_value);
          else env_map.put(key, value);
        }
        new_env = new String[env_map.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : env_map.entrySet()) {
          new_env[i] = entry.getKey() + "=" + entry.getValue();
          i += 1;
        }
      }
      Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commands), new_env, new File(homePath));
      CapturingProcessHandler processHandler = new CapturingProcessHandler(process);
      if (stdin != null) {
        final OutputStream processInput = processHandler.getProcessInput();
        assert processInput != null;
        processInput.write(stdin);
        processInput.write(26);    // EOF marker
        processInput.flush();
      }
      return processHandler.runProcess(timeout);
    }
    catch (IOException ex) {
      LOG.warn(ex);
      return failure_output;
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
