// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    final XmlDocument document = getElement().getDocument();
    List<XmlTag> rootTags = new ArrayList<>();
    if (document != null) {
      for (PsiElement element : document.getChildren())
        if (element instanceof XmlTag) rootTags.add((XmlTag)element);
    }

    Collection<StructureViewTreeElement> structureViewTreeElements =
      getStructureViewTreeElements(rootTags.toArray(XmlTag.EMPTY));

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
