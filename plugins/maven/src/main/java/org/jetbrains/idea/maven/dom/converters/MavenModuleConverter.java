package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

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

  protected void createReferences(PsiElement element,
                                  String resolvedText,
                                  TextRange range,
                                  VirtualFile virtualFile,
                                  XmlFile psiFile,
                                  List<PsiReference> result) {
    result.add(new MavenModulePsiReference(element, resolvedText, range, virtualFile, psiFile));
  }
}
