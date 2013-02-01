/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

class HtmlStructureViewTreeModel extends XmlStructureViewTreeModel implements PlaceHolder<String> {

  private final Collection<NodeProvider> myNodeProviders;

  private String myStructureViewPlace;

  public HtmlStructureViewTreeModel(final XmlFile file) {
    super(file);
    myNodeProviders = Arrays.<NodeProvider>asList(new Html5SectionsNodeProvider());
  }

  public void setPlace(final String place) {
    myStructureViewPlace = place;
  }

  public String getPlace() {
    return myStructureViewPlace;
  }

  @NotNull
  public Sorter[] getSorters() {
    return Sorter.EMPTY_ARRAY;
  }

  @NotNull
  public Collection<NodeProvider> getNodeProviders() {
    return myNodeProviders;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new HtmlFileTreeElement(ViewStructureAction.isInStructureViewPopup(this), (XmlFile)getPsiFile());
  }
}
