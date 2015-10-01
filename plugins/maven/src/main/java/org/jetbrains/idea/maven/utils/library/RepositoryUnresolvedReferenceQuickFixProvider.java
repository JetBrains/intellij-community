package org.jetbrains.idea.maven.utils.library;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public abstract class RepositoryUnresolvedReferenceQuickFixProvider
  extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  static private
  @NotNull
  String getFQTypeName(@NotNull PsiJavaCodeReferenceElement ref) {
    while (ref.getParent() != null && ref.getParent() instanceof PsiJavaCodeReferenceElement) {
      ref = (PsiJavaCodeReferenceElement)ref.getParent();
    }
    String name = ref.getCanonicalText();
    PsiFile file = ref.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return name;
    }
    String suffix = "." + name;
    PsiJavaFile javaFile = (PsiJavaFile)file;
    PsiImportList importList = javaFile.getImportList();
    if (importList != null) {
      for (PsiImportStatement importStatement : importList.getImportStatements()) {
        String qualifiedName = importStatement.getQualifiedName();
        if (qualifiedName != null && (qualifiedName.endsWith(suffix) || qualifiedName.equals(name))) {
          return qualifiedName;
        }
      }
    }
    return name;
  }

  protected abstract boolean isSuspectedName(@NotNull String fqTypeName);

  protected abstract
  @NotNull
  RepositoryLibraryDescription getLibraryDescription();

  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(ref);
    if (module != null && isSuspectedName(getFQTypeName(ref))) {
      registrar.register(new RepositoryAddLibraryAction(module, getLibraryDescription()));
    }
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
