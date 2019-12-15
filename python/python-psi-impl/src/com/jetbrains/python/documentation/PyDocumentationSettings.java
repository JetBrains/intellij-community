// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareModuleConfiguratorImpl;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyDocumentationSettings
  extends PyDefaultProjectAwareService<PyDocumentationSettings.ServiceState,
          PyDocumentationSettings,
          PyDocumentationSettings.AppService,
          PyDocumentationSettings.ModuleService> {

  private static final PyDefaultProjectAwareServiceClasses<ServiceState, PyDocumentationSettings, AppService, ModuleService>
    SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);

  final static DocStringFormat DEFAULT_DOC_STRING_FORMAT = DocStringFormat.PLAIN;
  private static final PyDocumentationSettingsDetector DETECTOR = new PyDocumentationSettingsDetector();

  protected PyDocumentationSettings() {
    super(new ServiceState());
  }

  public static PyDocumentationSettings getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }

  @NotNull
  public static PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES, DETECTOR);
  }


  public final boolean isNumpyFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.NUMPY);
  }

  public final boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(@Nullable PsiFile file, @NotNull DocStringFormat format) {
    return file instanceof PyFile ? getFormatForFile(file) == format : getState().getFormat() == format;
  }

  @NotNull
  public final DocStringFormat getFormatForFile(@NotNull PsiFile file) {
    final DocStringFormat fileFormat = getFormatFromDocformatAttribute(file);
    return fileFormat != null && fileFormat != DocStringFormat.PLAIN ? fileFormat : getState().myDocStringFormat;
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

  @NotNull
  public final DocStringFormat getFormat() {
    return getState().getFormat();
  }

  public final void setFormat(@NotNull DocStringFormat format) {
    getState().myDocStringFormat = format;
  }


  public final boolean isAnalyzeDoctest() {
    return getState().myAnalyzeDoctest;
  }

  public final void setAnalyzeDoctest(boolean analyze) {
    getState().myAnalyzeDoctest = analyze;
  }

  public final boolean isRenderExternalDocumentation() {
    return getState().myRenderExternalDocumentation;
  }

  public final void setRenderExternalDocumentation(boolean renderExternalDocumentation) {
    getState().myRenderExternalDocumentation = renderExternalDocumentation;
  }


  public static final class ServiceState {
    @NotNull
    private DocStringFormat myDocStringFormat;
    @OptionTag("analyzeDoctest")
    public boolean myAnalyzeDoctest = true;
    @OptionTag("renderExternalDocumentation")
    public boolean myRenderExternalDocumentation;

    ServiceState(@NotNull DocStringFormat docStringFormat) {
      myDocStringFormat = docStringFormat;
    }

    ServiceState() {
      this(DEFAULT_DOC_STRING_FORMAT);
    }

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
  }

  @State(name = "AppPyDocumentationSettings", storages = @Storage("PyDocumentationSettings.xml"))
  public static final class AppService extends PyDocumentationSettings {
  }


  @State(name = "PyDocumentationSettings")
  public static final class ModuleService extends PyDocumentationSettings {
  }
}
