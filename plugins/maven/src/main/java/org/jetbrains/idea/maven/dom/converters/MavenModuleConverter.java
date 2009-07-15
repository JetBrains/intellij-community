package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class MavenModuleConverter extends MavenReferenceConverter<PsiFile> {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString(@Nullable PsiFile psiFile, ConvertContext context) {
    VirtualFile file = context.getFile().getOriginalFile().getVirtualFile();
    return MavenModulePsiReference.calcRelativeModulePath(file, psiFile.getVirtualFile());
  }

  protected PsiReference createReference(PsiElement element, String text, TextRange range) {
    return new MavenModulePsiReference(element, text, range);
  }
}
