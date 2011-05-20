package com.intellij.structuralsearch.equivalence.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptor;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorBuilder;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlEquivalenceDescriptorProvider extends EquivalenceDescriptorProvider {

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @Override
  public EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element) {
    final EquivalenceDescriptorBuilder builder = new EquivalenceDescriptorBuilder();

    if (element instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element;
      return builder
        .constant(tag.getName())
        .inAnyOrder(tag.getAttributes())
        .codeBlock(tag.getSubTags())
        .elements(tag.getValue().getTextElements());
    }
    else if (element instanceof XmlDocument) {
      return builder.codeBlock(element.getChildren());
    }

    return null;
  }

}
