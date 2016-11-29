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
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Html5SectionsNodeProvider implements FileStructureNodeProvider<Html5SectionTreeElement>, PropertyOwner,
                                                  ActionShortcutProvider {

  public static final String ACTION_ID = "HTML5_OUTLINE_MODE";
  public static final String HTML5_OUTLINE_PROVIDER_PROPERTY = "html5.sections.node.provider";

  @Override
  @NotNull
  public String getName() {
    return ACTION_ID;
  }

  @Override
  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(XmlBundle.message("html5.outline.mode"), null, AllIcons.Xml.Html5);
  }

  @NotNull
  @Override
  public String getCheckBoxText() {
    return XmlBundle.message("html5.outline.mode");
  }

  @NotNull
  @Override
  public String getActionIdForShortcut() {
    return "FileStructurePopup";
  }

  @NotNull
  @Override
  public Shortcut[] getShortcut() {
    throw new IncorrectOperationException("see getActionIdForShortcut()");
  }

  @Override
  @NotNull
  public String getPropertyName() {
    return HTML5_OUTLINE_PROVIDER_PROPERTY;
  }

  @NotNull
  @Override
  public Collection<Html5SectionTreeElement> provideNodes(@NotNull final TreeElement node) {
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
