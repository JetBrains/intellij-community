package com.jetbrains.python.documentation;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author yole
 */
public class EpydocRunner {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.documentation.EpydocRunner");

  private EpydocRunner() {
  }

  @Nullable
  public static String formatDocstring(Module module, String text) {
    Sdk sdk = PythonSdkType.findPython2Sdk(module);
    if (sdk == null) {
      return null;
    }
    final Charset charset = EncodingProjectManager.getInstance(module.getProject()).getDefaultCharset();
    String sdkHome = sdk.getHomePath();
    final String formatter = PythonHelpersLocator.getHelperPath("epydoc_formatter.py");
    final ByteBuffer encoded = charset.encode(text);
    final byte[] data = new byte[encoded.limit()];
    encoded.get(data);
    ProcessOutput output = SdkUtil.getProcessOutput(new File(sdkHome).getParent(),
                                                    new String[] {
                                                      sdkHome,
                                                      formatter
                                                    },
                                                    null,
                                                    2000,
                                                    data);
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
