// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html.structureView;

import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.psi.filters.XmlTagFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class HtmlFileTreeElement extends PsiTreeElementBase<XmlFile> {
  private final boolean myInStructureViewPopup;

  HtmlFileTreeElement(final boolean inStructureViewPopup, final XmlFile xmlFile) {
    super(xmlFile);
    myInStructureViewPopup = inStructureViewPopup;
  }

  @Override
  public @NotNull @Unmodifiable Collection<StructureViewTreeElement> getChildrenBase() {
    if (isHtml5SectionsMode()) {
      return Collections.emptyList(); // Html5SectionsNodeProvider will return its structure
    }

    final XmlFile xmlFile = getElement();
    final XmlDocument document = xmlFile == null ? null : xmlFile.getDocument();
    if (document == null) {
      return Collections.emptyList();
    }

    final List<XmlTag> rootTags = new SmartList<>();
    document.processElements(new FilterElementProcessor(XmlTagFilter.INSTANCE, rootTags), document);

    if (rootTags.isEmpty()) {
      return Collections.emptyList();
    }
    else if (rootTags.size() == 1) {
      final XmlTag rootTag = rootTags.get(0);
      if ("html".equalsIgnoreCase(rootTag.getLocalName())) {
        final XmlTag[] subTags = rootTag.getSubTags();
        if (subTags.length == 1 &&
            ("head".equalsIgnoreCase(subTags[0].getLocalName()) || "body".equalsIgnoreCase(subTags[0].getLocalName()))) {
          return new HtmlTagTreeElement(subTags[0]).getChildrenBase();
        }
        return new HtmlTagTreeElement(rootTag).getChildrenBase();
      }

      return Collections.singletonList(new HtmlTagTreeElement(rootTag));
    }
    else {
      final Collection<StructureViewTreeElement> result = new ArrayList<>(rootTags.size());
      for (XmlTag tag : rootTags) {
        result.add(new HtmlTagTreeElement(tag));
      }
      return result;
    }
  }

  private boolean isHtml5SectionsMode() {
    final XmlFile xmlFile = getElement();
    if (xmlFile == null) return false;

    if (myInStructureViewPopup) {
      final String propertyName = TreeStructureUtil.getPropertyName(Html5SectionsNodeProvider.HTML5_OUTLINE_PROVIDER_PROPERTY);
      if (PropertiesComponent.getInstance().getBoolean(propertyName)) {
        return true;
      }
    }
    else if (StructureViewFactoryEx.getInstanceEx(xmlFile.getProject()).isActionActive(Html5SectionsNodeProvider.ACTION_ID)) {
      return true;
    }

    return false;
  }

  @Override
  public @Nullable String getPresentableText() {
    return toString(); // root element is not visible
  }
}