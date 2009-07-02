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
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public abstract class MavenReferenceConverter<T> extends Converter<T> implements CustomReferenceConverter<T> {
  @NotNull
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    Pair<VirtualFile, DomFileElement<MavenDomProjectModel>> fileAndDom = getFileAndDom(context);

    List<PsiReference> result = new ArrayList<PsiReference>();
    TextRange range = ElementManipulators.getValueTextRange(element);

    String originalText = value.getStringValue();
    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(originalText);
    while (matcher.find()) {
      String text = matcher.group(1);
      int from = range.getStartOffset() + matcher.start(1);

      result.add(new MavenPropertyPsiReference(element, text, from));
    }

    String resolvedText = MavenPropertyResolver.resolve(originalText, fileAndDom.second);
    XmlFile psiFile = context.getFile();
    createReferences(element, resolvedText, range, fileAndDom.first, psiFile, result);
    return result.toArray(new PsiReference[result.size()]);
  }

  protected abstract void createReferences(PsiElement element,
                                           String resolvedText,
                                           TextRange range,
                                           VirtualFile virtualFile,
                                           XmlFile psiFile,
                                           List<PsiReference> result);

  private Pair<VirtualFile, DomFileElement<MavenDomProjectModel>> getFileAndDom(ConvertContext context) {
    DomFileElement<MavenDomProjectModel> dom = DomUtil.getFileElement(context.getInvocationElement());
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return Pair.create(virtualFile, dom);
  }

  protected VirtualFile getFile(ConvertContext context) {
    return getFileAndDom(context).first;
  }
}
