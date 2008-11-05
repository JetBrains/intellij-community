package org.jetbrains.idea.maven.dom;

import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;

public class MavenDomElementDescriptorProvider implements XmlElementDescriptorProvider {
  public XmlElementDescriptor getDescriptor(XmlTag tag) {
    return MavenDomElementDescriptorHolder.getInstance(tag.getProject()).getDescriptor(tag);
  }
}
