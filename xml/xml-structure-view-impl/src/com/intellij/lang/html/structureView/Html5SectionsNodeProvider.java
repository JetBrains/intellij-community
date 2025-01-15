// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ActionShortcutProvider;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.util.PropertyOwner;
import com.intellij.psi.filters.XmlTagFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Html5SectionsNodeProvider implements FileStructureNodeProvider<Html5SectionTreeElement>, PropertyOwner,
                                                  ActionShortcutProvider {

  @SuppressWarnings("UnresolvedPluginConfigReference")
  public static final String ACTION_ID = "HTML5_OUTLINE_MODE";
  public static final String HTML5_OUTLINE_PROVIDER_PROPERTY = "html5.sections.node.provider";

  @Override
  public @NotNull String getName() {
    return ACTION_ID;
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(XmlPsiBundle.message("html5.outline.mode"), null, AllIcons.Xml.Html5);
  }

  @Override
  public @NotNull String getCheckBoxText() {
    return XmlPsiBundle.message("html5.outline.mode");
  }

  @Override
  public @NotNull String getActionIdForShortcut() {
    return "FileStructurePopup";
  }

  @Override
  public Shortcut @NotNull [] getShortcut() {
    throw new IncorrectOperationException("see getActionIdForShortcut()");
  }

  @Override
  public @NotNull String getPropertyName() {
    return HTML5_OUTLINE_PROVIDER_PROPERTY;
  }

  @Override
  public @NotNull Collection<Html5SectionTreeElement> provideNodes(final @NotNull TreeElement node) {
    if (!(node instanceof HtmlFileTreeElement)) return Collections.emptyList();

    final XmlFile xmlFile = ((HtmlFileTreeElement)node).getElement();
    final XmlDocument document = xmlFile == null ? null : xmlFile.getDocument();
    if (document == null) return Collections.emptyList();

    final List<XmlTag> rootTags = new ArrayList<>();
    document.processElements(new FilterElementProcessor(XmlTagFilter.INSTANCE, rootTags), document);

    final Collection<Html5SectionTreeElement> result = new ArrayList<>();

    for (XmlTag tag : rootTags) {
      result.addAll(Html5SectionsProcessor.processAndGetRootSections(tag));
    }

    return result;
  }
}
