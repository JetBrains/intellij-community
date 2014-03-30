package com.intellij.xml.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlPrefixReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    XmlAttributeValue attributeValue = (XmlAttributeValue)element;
    String value = attributeValue.getValue();
    if (value == null) return PsiReference.EMPTY_ARRAY;
    int i = value.indexOf(':');
    if (i <= 0) return PsiReference.EMPTY_ARRAY;
    PsiElement parent = attributeValue.getParent();
    if (parent instanceof XmlAttribute && !XmlNSDescriptorImpl.checkSchemaNamespace(((XmlAttribute)parent).getParent())) {
      XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
      if (descriptor instanceof XmlAttributeDescriptorImpl) {
        String type = ((XmlAttributeDescriptorImpl)descriptor).getType();
        if (type != null && type.endsWith(":QName")) {
          String prefix = XmlUtil.findPrefixByQualifiedName(type);
          String ns = ((XmlTag)descriptor.getDeclaration()).getNamespaceByPrefix(prefix);
          if (XmlNSDescriptorImpl.checkSchemaNamespace(ns)) {
            return new PsiReference[] {
              new SchemaPrefixReference(attributeValue, TextRange.from(1, i), value.substring(0, i), null)
            };
          }
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
