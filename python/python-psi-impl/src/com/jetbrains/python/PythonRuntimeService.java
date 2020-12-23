package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class PythonRuntimeService {

  public boolean isInPydevConsole(@NotNull PsiElement file) {
    return false;
  }

  @Nullable
  public Sdk getConsoleSdk(@NotNull PsiElement foothold) {
    return null;
  }

  public String createPydevDoc(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @NotNull
  public LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    return LanguageLevel.getDefault();
  }

  public PsiPolyVariantReference getPydevConsoleReference(@NotNull PyReferenceExpression element,
                                                          @NotNull PyResolveContext context) {
    return null;
  }

  public PythonConsoleData getPythonConsoleData(@Nullable ASTNode node) {
    return null;
  }

  public String formatDocstring(Module module, DocStringFormat format, String docstring) {
    return null;
  }

  public String mapToRemote(@NotNull String localRoot, @NotNull Sdk sdk) {
    return localRoot;
  }

  public boolean isInScratchFile(@NotNull PsiElement element) {
    return false;
  }

  public static PythonRuntimeService getInstance() {
    return ApplicationManager.getApplication().getService(PythonRuntimeService.class);
  }
}
