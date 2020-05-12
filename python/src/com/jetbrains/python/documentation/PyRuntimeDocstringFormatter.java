// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PyBundle;
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
import java.util.ArrayList;

public class PyRuntimeDocstringFormatter {
  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  @Nullable
  public static String runExternalTool(@NotNull final Module module,
                                @NotNull final DocStringFormat format,
                                @NotNull final String docstring) {
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
      LOG.warn("Python SDK for docstring formatter " + format +  " is not found");
      return "<p color=\"red\">" + missingInterpreterMessage + "</p>";
    }

    final String sdkHome = sdk.getHomePath();
    if (sdkHome == null) return null;

    final ByteBuffer encoded = DEFAULT_CHARSET.encode(docstring);
    final byte[] data = new byte[encoded.limit()];
    encoded.get(data);

    final ArrayList<String> arguments = Lists.newArrayList(format.getFormatterCommand());
    final GeneralCommandLine commandLine = PythonHelper.DOCSTRING_FORMATTER.newCommandLine(sdk, arguments);
    commandLine.setCharset(DEFAULT_CHARSET);

    LOG.debug("Command for launching docstring formatter: " + commandLine.getCommandLineString());

    final ProcessOutput output = PySdkUtil.getProcessOutput(commandLine, new File(sdkHome).getParent(), null, 5000, data, false);
    if (!output.checkSuccess(LOG)) {
      LOG.info("Malformed docstring:\n" + docstring);
      return null;
    }
    return output.getStdout();
  }
}
