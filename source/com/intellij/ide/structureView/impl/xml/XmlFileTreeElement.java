/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final XmlDocument document = getElement().getDocument();
    List<XmlTag> rootTags = new ArrayList<XmlTag>();
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
      final ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>(
        dtdStructureViewTreeElements.size() + structureViewTreeElements.size()
      );

      result.addAll(dtdStructureViewTreeElements);
      result.addAll(structureViewTreeElements);
      structureViewTreeElements = result;
    }
    return structureViewTreeElements;
  }

  public String getPresentableText() {
    return getElement().getName();
  }
}
