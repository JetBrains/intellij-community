package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface XmlElementDescriptorProvider {
  ExtensionPointName<XmlElementDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.elementDescriptorProvider");

  @Nullable
  XmlElementDescriptor getDescriptor(XmlTag tag);
}
