/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XmlFileTreeElement extends AbstractXmlTagTreeElement<XmlFile> {
  public XmlFileTreeElement(XmlFile file) {
    super(file);
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final XmlDocument document = getElement().getDocument();
    List<XmlTag> rootTags = new ArrayList<>();
    if (document != null) {
      for (PsiElement element : document.getChildren())
        if (element instanceof XmlTag) rootTags.add((XmlTag)element);
    }

    Collection<StructureViewTreeElement> structureViewTreeElements =
      getStructureViewTreeElements(rootTags.toArray(new XmlTag[rootTags.size()]));

    Collection<StructureViewTreeElement> dtdStructureViewTreeElements = null;
    final XmlProlog prolog = document != null ? document.getProlog():null;
    if (prolog != null) {
      final XmlDoctype doctype = prolog.getDoctype();

      if (doctype != null) {
        final XmlMarkupDecl xmlMarkupDecl = doctype.getMarkupDecl();
        if (xmlMarkupDecl != null) {
          dtdStructureViewTreeElements = DtdFileTreeElement.collectElements(xmlMarkupDecl);
        }
      }
    }

    if (dtdStructureViewTreeElements != null) {
      final ArrayList<StructureViewTreeElement> result = new ArrayList<>(
        dtdStructureViewTreeElements.size() + structureViewTreeElements.size()
      );

      result.addAll(dtdStructureViewTreeElements);
      result.addAll(structureViewTreeElements);
      structureViewTreeElements = result;
    }
    return structureViewTreeElements;
  }

  @Override
  public String getPresentableText() {
    return getElement().getName();
  }
}
