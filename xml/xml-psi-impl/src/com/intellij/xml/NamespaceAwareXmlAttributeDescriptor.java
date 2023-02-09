package com.intellij.xml;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NamespaceAwareXmlAttributeDescriptor extends XmlAttributeDescriptor {
  @Nullable
  String getNamespace(@NotNull XmlTag context);
}
