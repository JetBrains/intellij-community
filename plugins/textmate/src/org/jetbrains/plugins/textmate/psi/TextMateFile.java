package org.jetbrains.plugins.textmate.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.TextMateLanguage;

public class TextMateFile extends PsiFileBase {
  public TextMateFile(FileViewProvider provider) {
    super(provider, TextMateLanguage.LANGUAGE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return TextMateFileType.INSTANCE;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
