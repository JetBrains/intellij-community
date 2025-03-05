// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public abstract class PyDocumentationSettings
  extends PyDefaultProjectAwareService<PyDocumentationSettings.ServiceState,
          PyDocumentationSettings,
          PyDocumentationSettings.AppService,
          PyDocumentationSettings.ModuleService> {

  @ApiStatus.Internal
  public static final PyDefaultProjectAwareServiceClasses<ServiceState, PyDocumentationSettings, AppService, ModuleService>
    SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);

  @ApiStatus.Internal public static final DocStringFormat DEFAULT_DOC_STRING_FORMAT = DocStringFormat.REST;

  protected PyDocumentationSettings() {
    super(new ServiceState());
  }

  public static PyDocumentationSettings getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }


  public final boolean isNumpyFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.NUMPY);
  }

  public final boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(@Nullable PsiFile file, @NotNull DocStringFormat format) {
    return file instanceof PyAstFile ? getFormatForFile(file) == format : getState().getFormat() == format;
  }

  public final @NotNull DocStringFormat getFormatForFile(@NotNull PsiFile file) {
    final DocStringFormat fileFormat = getFormatFromDocformatAttribute(file);
    return fileFormat != null && fileFormat != DocStringFormat.PLAIN ? fileFormat : getState().myDocStringFormat;
  }

  public static @Nullable DocStringFormat getFormatFromDocformatAttribute(@NotNull PsiFile file) {
    if (file instanceof PyAstFile) {
      final PyAstTargetExpression expr = getDocFormatAttribute(file);
      if (expr != null) {
        final String docformat = PyPsiUtilsCore.strValue(expr.findAssignedValue());
        if (docformat != null) {
          final List<String> words = StringUtil.split(docformat, " ");
          if (!words.isEmpty()) {
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

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(@NotNull PsiFile file) {
    StubElement<?> stub = ((PsiFileImpl)file).getGreenStub();
    if (stub != null) {
      return getDocFormatAttribute(stub.getChildrenStubs());
    }
    else {
      return getDocFormatAttribute(file.getChildren());
    }
  }

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(@NotNull List<StubElement<?>> stubs) {
    for (StubElement<?> stub : stubs) {
      PsiElement psi = stub.getPsi();
      if (psi instanceof PyAstTargetExpression targetExpression && PyNames.DOCFORMAT.equals(targetExpression.getName())) {
        return targetExpression;
      }
      if (psi instanceof PyAstIfPart || psi instanceof PyAstElsePart) {
        PyAstTargetExpression targetExpression = getDocFormatAttribute(stub.getChildrenStubs());
        if (targetExpression != null) return targetExpression;
      }
    }
    return null;
  }

  private static @Nullable PyAstTargetExpression getDocFormatAttribute(@NotNull PsiElement @NotNull [] elements) {
    for (PsiElement element : elements) {
      if (element instanceof PyAstClass || element instanceof PyAstFunction) continue;
      if (element instanceof PyAstTargetExpression targetExpression && PyNames.DOCFORMAT.equals(targetExpression.getName())) {
        return targetExpression;
      }
      PyAstTargetExpression targetExpression = getDocFormatAttribute(element.getChildren());
      if (targetExpression != null) return targetExpression;
    }
    return null;
  }

  public final @NotNull DocStringFormat getFormat() {
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
    private @NotNull DocStringFormat myDocStringFormat;
    @OptionTag("analyzeDoctest")
    public boolean myAnalyzeDoctest = true;
    @OptionTag("renderExternalDocumentation")
    public boolean myRenderExternalDocumentation;

    @ApiStatus.Internal
    public ServiceState(@NotNull DocStringFormat docStringFormat) {
      myDocStringFormat = docStringFormat;
    }

    ServiceState() {
      this(DEFAULT_DOC_STRING_FORMAT);
    }

    public @NotNull DocStringFormat getFormat() {
      return myDocStringFormat;
    }

    public void setFormat(@NotNull DocStringFormat format) {
      myDocStringFormat = format;
    }

    // Legacy name of the field to preserve settings format
    @SuppressWarnings("unused")
    @OptionTag("myDocStringFormat")
    public @NotNull String getFormatName() {
      return myDocStringFormat.getName();
    }

    @SuppressWarnings("unused")
    public void setFormatName(@NotNull String name) {
      myDocStringFormat = DocStringFormat.fromNameOrPlain(name);
    }
  }

  @State(name = "AppPyDocumentationSettings", storages = @Storage("PyDocumentationSettings.xml"), category = SettingsCategory.CODE)
  public static final class AppService extends PyDocumentationSettings {
  }


  @State(name = "PyDocumentationSettings")
  public static final class ModuleService extends PyDocumentationSettings {
  }
}
