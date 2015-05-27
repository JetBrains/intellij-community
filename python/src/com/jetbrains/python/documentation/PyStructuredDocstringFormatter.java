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
package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyStructuredDocstringFormatter {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.documentation.PyStructuredDocstringFormatter");

  private PyStructuredDocstringFormatter() {
  }

  @Nullable
  public static List<String> formatDocstring(@NotNull final PsiElement element, @NotNull final String docstring) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      final Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length == 0) return Lists.newArrayList();
      module = modules[0];
    }
    if (module == null) return Lists.newArrayList();
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    final List<String> result = new ArrayList<String>();

    final String[] lines = PyDocumentationBuilder.removeCommonIndentation(docstring);
    final String preparedDocstring = StringUtil.join(lines, "\n");

    final String formatter;
    final StructuredDocStringBase structuredDocString;
    if (documentationSettings.isEpydocFormat(element.getContainingFile()) ||
        DocStringUtil.isEpydocDocString(preparedDocstring)) {
      formatter = PythonHelpersLocator.getHelperPath("epydoc_formatter.py");
      structuredDocString = new EpydocString(preparedDocstring);
      result.add(formatStructuredDocString(structuredDocString));
    }
    else if (documentationSettings.isReSTFormat(element.getContainingFile()) ||
             DocStringUtil.isSphinxDocString(preparedDocstring)) {
      formatter = PythonHelpersLocator.getHelperPath("rest_formatter.py");
      structuredDocString = new SphinxDocString(preparedDocstring);
    }
    else {
      return null;
    }

    final String output = runExternalTool(module, formatter, docstring);
    if (output != null)
      result.add(0, output);
    else
      result.add(0, structuredDocString.getDescription());

    return result;
  }

  @Nullable
  private static String runExternalTool(@NotNull final Module module,
                                        @NotNull final String formatter,
                                        @NotNull final String docstring) {
    final Sdk sdk = PythonSdkType.findPython2Sdk(module);
    if (sdk == null) return null;

    final String sdkHome = sdk.getHomePath();
    if (sdkHome == null) return null;

    final Charset charset = EncodingProjectManager.getInstance(module.getProject()).getDefaultCharset();

    final ByteBuffer encoded = charset.encode(docstring);
    final byte[] data = new byte[encoded.limit()];
    encoded.get(data);

    final Map<String, String> env = new HashMap<String, String>();
    PythonEnvUtil.setPythonDontWriteBytecode(env);

    final ProcessOutput output = PySdkUtil.getProcessOutput(new File(sdkHome).getParent(), new String[]{sdkHome, formatter},
                                                            env, 5000, data, true);
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

    final List<String> additionalTags = docString.getAdditionalTags();
    if (!additionalTags.isEmpty()) {
      result.append("<br/><br/><b>Additional:</b><br/>");
      result.append("<table>");
      for (String tagName : additionalTags) {
        final List<Substring> args = docString.getTagArguments(tagName);
        for (Substring arg : args) {
          final String s = arg.toString();
          result.append("<tr><td align=\"right\"><b>").append(tagName);
          result.append(" ").append(s).append(":</b>");
          result.append("</td><td>").append(docString.getTagValue(tagName, s)).append("</td></tr>");
        }
        result.append("</table>");
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
