package com.jetbrains.python.documentation;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * User : catherine
 */
public class ReSTRunner {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.documentation.ReSTRunner");

  private ReSTRunner() {
  }

  @Nullable
  public static String formatDocstring(@NotNull final Module module, String text) {
    Sdk sdk = PythonSdkType.findPython2Sdk(module);
    if (sdk == null) {
      return null;
    }
    String sdkHome = sdk.getHomePath();
    if (sdkHome == null) return null;
    final String formatter = PythonHelpersLocator.getHelperPath("rest_formatter.py");
    final Charset charset = EncodingProjectManager.getInstance(module.getProject()).getDefaultCharset();
    if (charset == null) return null;
    final ByteBuffer encoded = charset.encode(text);
    final byte[] data = new byte[encoded.limit()];
    encoded.get(data);
    ProcessOutput output = PySdkUtil.getProcessOutput(new File(sdkHome).getParent(),
                                                      new String[]{
                                                        sdkHome,
                                                        formatter
                                                      },
                                                      null, 5000, data);
    if (output.isTimeout()) {
      LOG.info("timeout when calculating docstring");
      return null;
    }
    else if (output.getExitCode() != 0) {
      final String error = "error when calculating docstring: " + output.getStderr();
      LOG.info(error);
      return null;
    }
    return output.getStdout();
  }
}
