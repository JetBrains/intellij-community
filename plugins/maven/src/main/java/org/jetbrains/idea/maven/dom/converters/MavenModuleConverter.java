package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
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
    VirtualFile file = getFile(context);
    return MavenModulePsiReference.calcRelativeModulePath(file, psiFile.getVirtualFile());
  }

  protected PsiReference createReference(PsiElement element,
                                         String text,
                                         TextRange range,
                                         VirtualFile virtualFile,
                                         XmlFile psiFile) {
    return new MavenModulePsiReference(element, text, range, virtualFile, psiFile);
  }
}
