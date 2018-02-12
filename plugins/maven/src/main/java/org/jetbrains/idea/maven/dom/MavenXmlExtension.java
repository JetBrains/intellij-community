/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * @since 4/4/2017
 */
public class MavenXmlExtension extends DefaultXmlExtension {
  @Override
  public boolean isAvailable(PsiFile file) {
    return file instanceof XmlFile && MavenDomUtil.isMavenFile(file);
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag, XmlElementDescriptor parentDescriptor) {
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
