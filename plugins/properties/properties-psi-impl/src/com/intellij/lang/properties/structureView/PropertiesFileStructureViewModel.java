// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.editor.PropertiesGroupingStructureViewModel;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class PropertiesFileStructureViewModel extends TextEditorBasedStructureViewModel implements PropertiesGroupingStructureViewModel {
  private final GroupByWordPrefixes myByWordPrefixesGrouper;
  private volatile boolean myGroupingState = true;
  public static final @NonNls String KIND_SORTER_ID = "KIND_SORTER";
  private static final Sorter KIND_SORTER = new Sorter() {
    @Override
    public @NotNull Comparator getComparator() {
      return (o1, o2) -> {
        int weight1 = o1 instanceof PropertiesPrefixGroup ? 1 : 0;
        int weight2 = o2 instanceof PropertiesPrefixGroup ? 1 : 0;
        return weight1 - weight2;
      };
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    public @NotNull ActionPresentation getPresentation() {
      String name = StructureViewBundle.message("action.sort.by.type");
      return new ActionPresentationData(name, name, AllIcons.ObjectBrowser.SortByType);
    }

    @Override
    public @NotNull String getName() {
      return KIND_SORTER_ID;
    }
  };

  public PropertiesFileStructureViewModel(PropertiesFileImpl file, Editor editor, String separator) {
    super(editor, file);

    myByWordPrefixesGrouper = new GroupByWordPrefixes(separator);
  }

  @Override
  public void setSeparator(String separator) {
    myByWordPrefixesGrouper.setSeparator(separator);
    PropertiesSeparatorManager separatorManager = PropertiesSeparatorManager.getInstance(getPsiFile().getProject());
    separatorManager.setSeparator(((PropertiesFileImpl)getPsiFile()).getResourceBundle(), separator);
  }

  @Override
  public String getSeparator() {
    return myByWordPrefixesGrouper.getSeparator();
  }

  @Override
  public void setGroupingActive(boolean state) {
    myGroupingState = state;
  }

  @Override
  public @NotNull StructureViewTreeElement getRoot() {
    return new PropertiesFileStructureViewElement((PropertiesFileImpl)getPsiFile(), () -> myGroupingState);
  }

  @Override
  public Grouper @NotNull [] getGroupers() {
    return new Grouper[]{myByWordPrefixesGrouper};
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER, KIND_SORTER};
  }

  @Override
  protected Class @NotNull [] getSuitableClasses() {
    return new Class[] {Property.class};
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return false;
  }
}
