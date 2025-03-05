// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.xml.DefaultXmlExtension;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

/**
 * @author Vladislav.Soroka
 */
public final class MavenXmlExtension extends DefaultXmlExtension {
  @Override
  public boolean isAvailable(PsiFile file) {
    return file instanceof XmlFile && MavenDomUtil.isMavenFile(file);
  }

  @Override
  public @Nullable XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag, XmlElementDescriptor parentDescriptor) {
    DomElement domElement = DomManagerImpl.getDomManager(tag.getProject()).getDomElement(contextTag);
    if (domElement != null) {
      MavenDomConfiguration configuration = DomUtil.getParentOfType(domElement, MavenDomConfiguration.class, false);
      if (configuration != null && configuration.getGenericInfo().getFixedChildrenDescriptions().isEmpty()) {
        return new AnyXmlElementDescriptor(null, null);
      }
    }
    return super.getElementDescriptor(tag, contextTag, parentDescriptor);
  }
}
