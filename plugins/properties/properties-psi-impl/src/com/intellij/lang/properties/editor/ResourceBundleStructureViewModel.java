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
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.lang.properties.structureView.PropertiesSeparatorManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ResourceBundleStructureViewModel implements PropertiesGroupingStructureViewModel {
  private final ResourceBundle myResourceBundle;
  private final GroupByWordPrefixes myByWordPrefixesGrouper;
  private final ResourceBundleFileStructureViewElement myRoot;

  public ResourceBundleStructureViewModel(ResourceBundle root) {
    myResourceBundle = root;
    String separator = PropertiesSeparatorManager.getInstance(root.getProject()).
      getSeparator(myResourceBundle);
    myByWordPrefixesGrouper = new GroupByWordPrefixes(separator);
    myRoot = new ResourceBundleFileStructureViewElement(myResourceBundle);
  }

  public void setSeparator(String separator) {
    myByWordPrefixesGrouper.setSeparator(separator);
    PropertiesSeparatorManager.getInstance(myResourceBundle.getProject()).setSeparator(myResourceBundle, separator);
  }

  public void setShowOnlyIncomplete(boolean showOnlyIncomplete) {
    myRoot.setShowOnlyIncomplete(showOnlyIncomplete);
  }

  public boolean isShowOnlyIncomplete() {
    return myRoot.isShowOnlyIncomplete();
  }

  public String getSeparator() {
    return myByWordPrefixesGrouper.getSeparator();
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{myByWordPrefixesGrouper};
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER};
  }

  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }

  public Object getCurrentEditorElement() {
    return null;
  }

  public void addEditorPositionListener(@NotNull FileEditorPositionListener listener) {

  }

  public void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) {

  }

  public void addModelListener(@NotNull ModelListener modelListener) {

  }

  public void removeModelListener(@NotNull ModelListener modelListener) {

  }

  public void dispose() {

  }

  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  @Override
  public boolean isAlwaysShowsPlus(final StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(final StructureViewTreeElement element) {
    return element instanceof ResourceBundlePropertyStructureViewElement;
  }
}
