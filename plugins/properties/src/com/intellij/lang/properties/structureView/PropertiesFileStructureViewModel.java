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
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.editor.PropertiesGroupingStructureViewModel;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author max
 */
public class PropertiesFileStructureViewModel extends TextEditorBasedStructureViewModel implements PropertiesGroupingStructureViewModel {
  private final PropertiesFileImpl myPropertiesFile;
  private final GroupByWordPrefixes myGroupByWordPrefixes;
  @NonNls public static final String KIND_SORTER_ID = "KIND_SORTER";
  private static final Sorter KIND_SORTER = new Sorter() {
    @NotNull
    public Comparator getComparator() {
      return new Comparator() {
        public int compare(final Object o1, final Object o2) {
          int weight1 = o1 instanceof PropertiesPrefixGroup ? 1 : 0;
          int weight2 = o2 instanceof PropertiesPrefixGroup ? 1 : 0;
          return weight1 - weight2;
        }
      };
    }

    public boolean isVisible() {
      return true;
    }

    @NotNull
    public ActionPresentation getPresentation() {
      String name = IdeBundle.message("action.sort.by.type");
      return new ActionPresentationData(name, name, AllIcons.ObjectBrowser.SortByType);
    }

    @NotNull
    public String getName() {
      return KIND_SORTER_ID;
    }
  };

  public PropertiesFileStructureViewModel(final PropertiesFileImpl root) {
    super(root);
    myPropertiesFile = root;
    String separator = PropertiesSeparatorManager.getInstance().getSeparator(root.getProject(), root.getVirtualFile());
    myGroupByWordPrefixes = new GroupByWordPrefixes(separator);
  }

  public void setSeparator(String separator) {
    myGroupByWordPrefixes.setSeparator(separator);
    PropertiesSeparatorManager.getInstance().setSeparator(myPropertiesFile.getVirtualFile(), separator);
  }

  public String getSeparator() {
    return myGroupByWordPrefixes.getSeparator();
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new PropertiesFileStructureViewElement(myPropertiesFile);
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{myGroupByWordPrefixes};
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER, KIND_SORTER};
  }

  protected PsiFile getPsiFile() {
    return myPropertiesFile;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
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
