package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenModel;

public class MavenModuleConverter extends Converter<PsiFile> implements CustomReferenceConverter<PsiFile> {
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    // let me know if it is called
    throw new UnsupportedOperationException();
  }

  public String toString(@Nullable PsiFile psiFile, ConvertContext context) {
    VirtualFile file = getFileAndDom(context).first;
    return MavenModuleReference.calcRelativeModulePath(file, psiFile.getVirtualFile());
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    Pair<VirtualFile, DomFileElement<MavenModel>> fileAndDom = getFileAndDom(context);

    String originalText = value.getStringValue();
    String resolvedText = PropertyResolver.resolve(originalText, fileAndDom.second);

    XmlFile psiFile = context.getFile();
    TextRange range = ElementManipulators.getValueTextRange(element);
    return new PsiReference[]{new MavenModuleReference(element, fileAndDom.first, psiFile, originalText, resolvedText, range)};
  }

  private Pair<VirtualFile, DomFileElement<MavenModel>> getFileAndDom(ConvertContext context) {
    DomFileElement<MavenModel> dom = context.getInvocationElement().getRoot();
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return Pair.create(virtualFile, dom);
  }
}
