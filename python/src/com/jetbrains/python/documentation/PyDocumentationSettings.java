// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
@State(name = "PyDocumentationSettings")
public class PyDocumentationSettings implements PersistentStateComponent<PyDocumentationSettings> {
  public static final DocStringFormat DEFAULT_DOCSTRING_FORMAT = DocStringFormat.REST;

  public static PyDocumentationSettings getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, PyDocumentationSettings.class);
  }

  @NotNull private DocStringFormat myDocStringFormat = DEFAULT_DOCSTRING_FORMAT;
  private boolean myAnalyzeDoctest = true;
  private boolean myRenderExternalDocumentation;

  public boolean isNumpyFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.NUMPY);
  }

  public boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(@Nullable PsiFile file, @NotNull DocStringFormat format) {
    return file instanceof PyFile ? getFormatForFile(file) == format : myDocStringFormat == format;
  }

  @NotNull
  public DocStringFormat getFormatForFile(@NotNull PsiFile file) {
    final DocStringFormat fileFormat = getFormatFromDocformatAttribute(file);
    return fileFormat != null && fileFormat != DocStringFormat.PLAIN ? fileFormat : myDocStringFormat;
  }

  @Nullable
  public static DocStringFormat getFormatFromDocformatAttribute(@NotNull PsiFile file) {
    if (file instanceof PyFile) {
      final PyTargetExpression expr = ((PyFile)file).findTopLevelAttribute(PyNames.DOCFORMAT);
      if (expr != null) {
        final String docformat = PyPsiUtils.strValue(expr.findAssignedValue());
        if (docformat != null) {
          final List<String> words = StringUtil.split(docformat, " ");
          if (words.size() > 0) {
            final DocStringFormat fileFormat = DocStringFormat.fromName(words.get(0));
            if (fileFormat != null) {
              return fileFormat;
            }
          }
        }
      }
    }
    return null;
  }

  @Transient
  @NotNull
  public DocStringFormat getFormat() {
    return myDocStringFormat;
  }

  public void setFormat(@NotNull DocStringFormat format) {
    myDocStringFormat = format;
  }

  // Legacy name of the field to preserve settings format
  @SuppressWarnings("unused")
  @OptionTag("myDocStringFormat")
  @NotNull
  public String getFormatName() {
    return myDocStringFormat.getName();
  }

  @SuppressWarnings("unused")
  public void setFormatName(@NotNull String name) {
    myDocStringFormat = DocStringFormat.fromNameOrPlain(name);
  }

  public boolean isAnalyzeDoctest() {
    return myAnalyzeDoctest;
  }

  public void setAnalyzeDoctest(boolean analyze) {
    myAnalyzeDoctest = analyze;
  }

  public boolean isRenderExternalDocumentation() {
    return myRenderExternalDocumentation;
  }

  public void setRenderExternalDocumentation(boolean renderExternalDocumentation) {
    myRenderExternalDocumentation = renderExternalDocumentation;
  }

  @Override
  public PyDocumentationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyDocumentationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
