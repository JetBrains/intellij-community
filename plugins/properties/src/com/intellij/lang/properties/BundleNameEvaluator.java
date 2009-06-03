package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface BundleNameEvaluator {

  BundleNameEvaluator DEFAULT = new BundleNameEvaluator() {

    @Nullable
    public String evaluateBundleName(final PsiFile psiFile) {
      final VirtualFile virtualFile = psiFile == null ? null : psiFile.getOriginalFile().getVirtualFile();
      if (virtualFile == null || !(psiFile instanceof PropertiesFile)) {
        return null;
      }

      final PsiDirectory directory = psiFile.getParent();
      final String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);

      if (packageQualifiedName != null) {
        final StringBuilder qName = new StringBuilder(packageQualifiedName);
        if (qName.length() > 0) {
          qName.append(".");
        }
        qName.append(PropertiesUtil.getBaseName(virtualFile));
        return qName.toString();
      }
      return null;
    }
  };

  @Nullable
  String evaluateBundleName(PsiFile psiFile);
}
