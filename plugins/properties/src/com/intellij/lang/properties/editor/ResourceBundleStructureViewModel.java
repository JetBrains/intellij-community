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
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.lang.properties.structureView.PropertiesSeparatorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ResourceBundleStructureViewModel implements PropertiesGroupingStructureViewModel, StructureViewModel.ExpandInfoProvider {
  private final ResourceBundle myResourceBundle;
  private final GroupByWordPrefixes myGroupByWordPrefixes;
  private final StructureViewTreeElement myRoot;

  public ResourceBundleStructureViewModel(final Project project, ResourceBundle root) {
    myResourceBundle = root;
    String separator = PropertiesSeparatorManager.getInstance().getSeparator(project, new ResourceBundleAsVirtualFile(myResourceBundle));
    myGroupByWordPrefixes = new GroupByWordPrefixes(separator);
    myRoot = new ResourceBundleFileStructureViewElement(project, myResourceBundle);
  }

  public void setSeparator(String separator) {
    myGroupByWordPrefixes.setSeparator(separator);
    PropertiesSeparatorManager.getInstance().setSeparator(new ResourceBundleAsVirtualFile(myResourceBundle), separator);
  }

  public String getSeparator() {
    return myGroupByWordPrefixes.getSeparator();
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{myGroupByWordPrefixes};
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

  @Override
  public boolean isAutoExpand(@NotNull StructureViewTreeElement element) {
    return getRoot() == element;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
