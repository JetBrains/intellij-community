// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.xml.impl.dtd.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public final class DtdFileTreeElement extends PsiTreeElementBase<XmlFile> {
  public DtdFileTreeElement(XmlFile file) {
    super(file);
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return collectElements(getElement().getDocument());
  }

  static List<StructureViewTreeElement> collectElements(final XmlElement element) {
    final List<StructureViewTreeElement> elements = new ArrayList<>();

    XmlUtil.processXmlElements(element, new PsiElementProcessor() {
      @Override
      public boolean execute(final @NotNull PsiElement element) {
        if (element instanceof XmlElementDecl ||
            element instanceof XmlEntityDecl) {
          elements.add(new DtdTreeElement((PsiNamedElement)element));
        }
        return true;
      }
    }, false);
    return elements;
  }

  @Override
  public String getPresentableText() {
    return getElement().getName();
  }

  private static final class DtdTreeElement extends PsiTreeElementBase<PsiNamedElement> {
    private static final @NonNls String IMPLIED = "implied";
    private static final @NonNls String REQUIRED = "required";
    private static final @NonNls String FIXED = "fixed";
    private static final @NonNls String ID = "id";
    private static final @NonNls String IDREF = "idref";
    private static final @NonNls String ENUM = "enum";

    DtdTreeElement(final PsiNamedElement element) {
      super(element);
    }

    @Override
    public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    @Override
    public String getPresentableText() {
      final PsiNamedElement namedElement = getElement();
      return namedElement != null ? namedElement.getName():"";
    }

    @Override
    public Icon getIcon(final boolean open) {
      return IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag);
    }

    @Override
    public String getLocationString() {
      final XmlElement owner = (XmlElement)getElement();

      final XmlAttlistDecl[] attLists= owner instanceof XmlElementDecl ? XmlElementDescriptorImpl.getCachedAttributeDeclarations(owner): XmlAttlistDecl.EMPTY_ARRAY;

      if (attLists.length > 0) {
        Map<String,XmlAttributeDecl> attrMap = null;

        final String name = getElement().getName();
        for(XmlAttlistDecl a:attLists) {
          final String aname = a.getName();
          if (!Objects.equals(aname, name)) continue;
          if (attrMap == null) attrMap = new LinkedHashMap<>();

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