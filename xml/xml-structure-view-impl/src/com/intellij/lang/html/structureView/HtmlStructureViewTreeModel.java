// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.html.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

class HtmlStructureViewTreeModel extends XmlStructureViewTreeModel implements PlaceHolder {

  private final Collection<NodeProvider> myNodeProviders;
  private String myStructureViewPlace;

  private static final Sorter HTML_ALPHA_SORTER = new Sorter() {
    @NotNull
    @Override
    public Comparator getComparator() {
      return new Comparator() {
        @Override
        public int compare(Object o1, Object o2) {
          String s1 = SorterUtil.getStringPresentation(o1);
          String s2 = SorterUtil.getStringPresentation(o2);

          if (isTagPresentation(s1, "head") && isTagPresentation(s2, "body")) return -1;
          if (isTagPresentation(s1, "body") && isTagPresentation(s2, "head")) return 1;

          return s1.compareToIgnoreCase(s2);
        }

        private boolean isTagPresentation(final String presentation, final String tagName) {
          // "head", "head#id", "head.cls"
          final String lowerCased = StringUtil.toLowerCase(presentation);
          return lowerCased.startsWith(tagName) &&
                 (lowerCased.length() == tagName.length() || !Character.isLetter(lowerCased.charAt(tagName.length())));
        }
      };
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    public String toString() {
      return getName();
    }

    @Override
    @NotNull
    public ActionPresentation getPresentation() {
      return new ActionPresentationData(PlatformEditorBundle.message("action.sort.alphabetically"),
                                        PlatformEditorBundle.message("action.sort.alphabetically"),
                                        AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    @NotNull
    public String getName() {
      return ALPHA_SORTER_ID;
    }
  };

  private static final Sorter[] ourSorters = {HTML_ALPHA_SORTER};

  HtmlStructureViewTreeModel(final XmlFile file, @Nullable Editor editor) {
    super(file, editor);

    myNodeProviders = Collections.singletonList(new Html5SectionsNodeProvider());
  }

  @Override
  public void setPlace(@NotNull final String place) {
    myStructureViewPlace = place;
  }

  @Override
  public String getPlace() {
    return myStructureViewPlace;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    if (TreeStructureUtil.isInStructureViewPopup(this)) {
      return Sorter.EMPTY_ARRAY;  // because in popup there's no option to disable sorter
    }

    return ourSorters;
  }

  @Override
  @NotNull
  public Collection<NodeProvider> getNodeProviders() {
    return myNodeProviders;
  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return new HtmlFileTreeElement(TreeStructureUtil.isInStructureViewPopup(this), getPsiFile());
  }
}
