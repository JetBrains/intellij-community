package com.intellij.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface XmlAttributeDescriptorsProvider {

  ExtensionPointName<XmlAttributeDescriptorsProvider> EP_NAME = new ExtensionPointName<XmlAttributeDescriptorsProvider>("com.intellij.xml.attributeDescriptorsProvider");

  XmlAttributeDescriptor[] getAttributeDescriptors(final XmlTag context);

  @Nullable
  XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final XmlTag context);

}
