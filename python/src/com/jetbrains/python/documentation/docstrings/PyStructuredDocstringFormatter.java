/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.docstrings;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStructuredDocstringFormatter {
  private static final Logger LOG = Logger.getInstance(PyStructuredDocstringFormatter.class);
  private static final Charset DEFAULT_CHARSET = CharsetToolkit.UTF8_CHARSET;

  private PyStructuredDocstringFormatter() {
  }

  /**
   * @param docstring docstring text without string literal prefix, without quotes and already escaped. 
   *                  Supposedly result of {@link PyStringLiteralExpression#getStringValue()}.
   */
  @Nullable
  public static List<String> formatDocstring(@NotNull final PsiElement element, @NotNull final String docstring) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      final Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length == 0) return Lists.newArrayList();
      module = modules[0];
    }
    if (module == null) return Lists.newArrayList();
    final List<String> result = new ArrayList<>();

    final String preparedDocstring = PyIndentUtil.removeCommonIndent(docstring, true).trim();

    final DocStringFormat format = DocStringUtil.guessDocStringFormat(preparedDocstring, element);
    if (format == DocStringFormat.PLAIN) {
      return null;
    }

    final StructuredDocString structuredDocString = DocStringUtil.parseDocStringContent(format, preparedDocstring);

    final String output = runExternalTool(module, format, preparedDocstring);
    if (output != null) {
      result.add(output);
    }
    else {
      result.add(structuredDocString.getDescription());
    }

    // Information about parameters in Epytext-style docstrings are formatter on our side
    if (format == DocStringFormat.EPYTEXT) {
      result.add(formatStructuredDocString(structuredDocString));
    }

    return result;
  }

  @Nullable
  private static String runExternalTool(@NotNull final Module module,
                                        @NotNull final DocStringFormat format,
                                        @NotNull final String docstring) {
    final Sdk sdk;
    final String missingInterpreterMessage;
    if (format == DocStringFormat.EPYTEXT) {
      sdk = PythonSdkType.findPython2Sdk(module);
      missingInterpreterMessage = PyBundle.message("QDOC.epydoc.python2.sdk.not.found");
    }
    else {
      sdk = PythonSdkType.findLocalCPython(module);
      missingInterpreterMessage = PyBundle.message("QDOC.sdk.not.found");
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

  private static String formatStructuredDocString(@NotNull final StructuredDocString docString) {
    final StringBuilder result = new StringBuilder();

    final String attributeDescription = docString.getAttributeDescription();
    if (attributeDescription != null) {
      result.append(attributeDescription);
      final String attrType = docString.getParamType(null);
      if (attrType != null) {
        result.append(" <i>Type: ").append(attrType).append("</i>");
      }
    }

    formatParameterDescriptions(docString, result, false);
    formatParameterDescriptions(docString, result, true);

    final String returnDescription = docString.getReturnDescription();
    final String returnType = docString.getReturnType();
    if (returnDescription != null || returnType != null) {
      result.append("<br><b>Return value:</b><br>");
      if (returnDescription != null) {
        result.append(returnDescription);
      }
      if (returnType != null) {
        result.append(" <i>Type: ").append(returnType).append("</i>");
      }
    }

    final List<String> raisedException = docString.getRaisedExceptions();
    if (raisedException.size() > 0) {
      result.append("<br><b>Raises:</b><br>");
      for (String s : raisedException) {
        result.append("<b>").append(s).append("</b> - ").append(docString.getRaisedExceptionDescription(s)).append("<br>");
      }
    }

    if (docString instanceof TagBasedDocString) {
      final TagBasedDocString taggedDocString = (TagBasedDocString)docString;
      final List<String> additionalTags = taggedDocString.getAdditionalTags();
      if (!additionalTags.isEmpty()) {
        result.append("<br/><br/><b>Additional:</b><br/>");
        result.append("<table>");
        for (String tagName : additionalTags) {
          final List<Substring> args = taggedDocString.getTagArguments(tagName);
          for (Substring arg : args) {
            final String s = arg.toString();
            result.append("<tr><td align=\"right\"><b>").append(tagName);
            result.append(" ").append(s).append(":</b>");
            result.append("</td><td>").append(taggedDocString.getTagValue(tagName, s)).append("</td></tr>");
          }
          result.append("</table>");
        }
      }
    }
    return result.toString();
  }

  private static void formatParameterDescriptions(@NotNull final StructuredDocString docString,
                                                  @NotNull final StringBuilder result,
                                                  boolean keyword) {
    final List<String> parameters = keyword ? docString.getKeywordArguments() : docString.getParameters();
    if (parameters.size() > 0) {
      result.append("<br><b>").append(keyword ? "Keyword arguments:" : "Parameters").append("</b><br>");
      for (String parameter : parameters) {
        final String description = keyword ? docString.getKeywordArgumentDescription(parameter) : docString.getParamDescription(parameter);
        result.append("<b>");
        result.append(parameter);
        result.append("</b>: ");
        if (description != null) {
          result.append(description);
        }
        final String paramType = docString.getParamType(parameter);
        if (paramType != null) {
          result.append(" <i>Type: ").append(paramType).append("</i>");
        }
        result.append("<br>");
      }
    }
  }
}
