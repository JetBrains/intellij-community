package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public abstract class MavenReferenceConverter<T> extends Converter<T> implements CustomReferenceConverter<T> {
  @NotNull
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    Pair<VirtualFile, DomFileElement<MavenDomProjectModel>> fileAndDom = getFileAndDom(context);
    String text = value.getStringValue();
    TextRange range = ElementManipulators.getValueTextRange(element);
    XmlFile psiFile = context.getFile();

    return new PsiReference[]{createReference(element, text, range, fileAndDom.first, psiFile)};
  }

  protected abstract PsiReference createReference(PsiElement element,
                                                  String text,
                                                  TextRange range,
                                                  VirtualFile virtualFile,
                                                  XmlFile psiFile);

  private Pair<VirtualFile, DomFileElement<MavenDomProjectModel>> getFileAndDom(ConvertContext context) {
    DomFileElement<MavenDomProjectModel> dom = DomUtil.getFileElement(context.getInvocationElement());
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return Pair.create(virtualFile, dom);
  }

  protected VirtualFile getFile(ConvertContext context) {
    return getFileAndDom(context).first;
  }
}
