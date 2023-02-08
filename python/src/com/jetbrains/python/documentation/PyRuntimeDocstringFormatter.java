// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PyRuntimeDocstringFormatter {
  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  @Nullable
  public static String runExternalTool(@NotNull final Module module,
                                       @NotNull final DocStringFormat format,
                                       @NotNull final String input,
                                       @NotNull final List<String> formatterFlags) {
    final Sdk sdk;
    final String missingInterpreterMessage;
    if (format == DocStringFormat.EPYTEXT) {
      sdk = PythonSdkType.findPython2Sdk(module);
      missingInterpreterMessage = PyPsiBundle.message("QDOC.epydoc.python2.sdk.not.found");
    }
    else {
      sdk = PythonSdkType.findLocalCPython(module);
      missingInterpreterMessage = PyPsiBundle.message("QDOC.local.sdk.not.found");
    }
    if (sdk == null) {
      LOG.warn("Python SDK for input formatter " + format + " is not found");
      return HtmlChunk.p().attr("color", ColorUtil.toHtmlColor(JBColor.RED)).addRaw(missingInterpreterMessage).toString();
    }

    final String sdkHome = sdk.getHomePath();
    if (sdkHome == null) return null;

    final ByteBuffer encoded = DEFAULT_CHARSET.encode(input);
    final byte[] data = new byte[encoded.limit()];
    encoded.get(data);

    final List<String> arguments = ContainerUtil.prepend(formatterFlags, "--format", format.getFormatterCommand());

    final GeneralCommandLine commandLine = PythonHelper.DOCSTRING_FORMATTER.newCommandLine(sdk, arguments);
    commandLine.setCharset(DEFAULT_CHARSET);

    LOG.debug("Command for launching docstring formatter: " + commandLine.getCommandLineString());

    final ProcessOutput output = PySdkUtil.getProcessOutput(commandLine, new File(sdkHome).getParent(), null, 5000, data, false);
    if (!output.checkSuccess(LOG)) {
      LOG.info("Malformed input:\n" + input);
      return null;
    }
    return output.getStdout();
  }
}
