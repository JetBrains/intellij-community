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
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.Icons;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class DtdFileTreeElement extends PsiTreeElementBase<XmlFile> {
  public DtdFileTreeElement(XmlFile file) {
    super(file);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return collectElements(getElement().getDocument());
  }

  static List<StructureViewTreeElement> collectElements(final XmlElement element) {
    final List<StructureViewTreeElement> elements = new ArrayList<StructureViewTreeElement>();

    XmlUtil.processXmlElements(element, new PsiElementProcessor() {
      public boolean execute(final PsiElement element) {
        if (element instanceof XmlElementDecl ||
            element instanceof XmlEntityDecl) {
          elements.add(new DtdTreeElement((PsiNamedElement)element));
        }
        return true;
      }
    }, false);
    return elements;
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  private static class DtdTreeElement extends PsiTreeElementBase<PsiNamedElement> {
    @NonNls private static final String IMPLIED = "implied";
    @NonNls private static final String REQUIRED = "required";
    @NonNls private static final String FIXED = "fixed";
    @NonNls private static final String ID = "id";
    @NonNls private static final String IDREF = "idref";
    @NonNls private static final String ENUM = "enum";

    public DtdTreeElement(final PsiNamedElement element) {
      super(element);
    }

    @NotNull
    public Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    public String getPresentableText() {
      final PsiNamedElement namedElement = getElement();
      return namedElement != null ? namedElement.getName():"";
    }

    public Icon getIcon(final boolean open) {
      return Icons.XML_TAG_ICON;
    }

    private static final Key<CachedValue<List<XmlAttlistDecl>>> MY_CACHED_ATTLISTS = Key.create("Dtd.CachedAttLists");

    private static UserDataCache<CachedValue<List<XmlAttlistDecl>>, PsiFile, Object> ourCachedAttListDefsCache = new UserDataCache<CachedValue<List<XmlAttlistDecl>>, PsiFile, Object>() {
      protected CachedValue<List<XmlAttlistDecl>> compute(final PsiFile psiFile, final Object p) {
        return psiFile.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<List<XmlAttlistDecl>>() {
          public Result<List<XmlAttlistDecl>> compute() {
            final List<XmlAttlistDecl> attLists=new ArrayList<XmlAttlistDecl>(1);

            XmlUtil.processXmlElements((XmlFile)psiFile, new PsiElementProcessor() {
              public boolean execute(final PsiElement element) {
                if (element instanceof XmlAttlistDecl) {
                  attLists.add((XmlAttlistDecl)element);
                }
                return true;
              }
            }, true, false);
            return new Result<List<XmlAttlistDecl>>(attLists);
          }
        }, false);
      }
    };

    public String getLocationString() {
      final List<XmlAttlistDecl> attLists= ourCachedAttListDefsCache.get(MY_CACHED_ATTLISTS, getElement().getContainingFile(), null).getValue();

      if (!attLists.isEmpty()) {
        Map<String,XmlAttributeDecl> attrMap = null;

        final String name = getElement().getName();
        for(XmlAttlistDecl a:attLists) {
          final XmlElement element = a.getNameElement();
          if (!name.equals(element != null ? element.getText():null)) continue;
          if (attrMap == null) attrMap = new LinkedHashMap<String, XmlAttributeDecl>();

          for(XmlAttributeDecl d : a.getAttributeDecls()) {
            attrMap.put(d.getName(), d);
          }
        }

        StringBuilder b = null;
        if (attrMap != null) {
          for(Map.Entry<String,XmlAttributeDecl> e:attrMap.entrySet()) {
            if (b == null) b = new StringBuilder();
            else b.append(", ");
            b.append(e.getKey());
            final XmlAttributeDecl attributeDecl = e.getValue();
            String type = null;

            if (attributeDecl.isIdAttribute()) {
              type = ID;
            } else if (attributeDecl.isIdRefAttribute()) {
              type = IDREF;
            } else if (attributeDecl.isEnumerated()) {
              type = ENUM;
            }

            if (attributeDecl.isAttributeFixed()) {
              if (type == null) type = FIXED;
              else type += " " + FIXED;
            } else if (attributeDecl.isAttributeRequired()) {
              if (type == null) type = REQUIRED;
              else type += " " + REQUIRED;
            } else if (attributeDecl.isAttributeImplied()) {
              if (type == null) type = IMPLIED;
              else type += " " + IMPLIED;
            }

            if (type != null) b.append(':').append(type);
            final XmlAttributeValue value = attributeDecl.getDefaultValue();

            if (value != null) b.append("=").append(value);
          }
        }

        if (b != null) return b.toString();
     }
     return super.getLocationString();
    }

    public String toString() {
      final String s = getLocationString();
      final String name = getElement().getName();
      if (s == null || s.length() == 0) return name;
      return name + " (" + s + ")";
    }
  }


}