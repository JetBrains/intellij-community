/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;

public class DomStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;
  private final XmlFile myFile;

  public DomStructureViewBuilder(final XmlFile file, final Function<DomElement,DomService.StructureViewMode> descriptor) {
    myFile = file;
    myDescriptor = descriptor;
  }

  @NotNull
  public StructureViewModel createStructureViewModel() {
    return new DomStructureViewTreeModel(myFile, myDescriptor);
  }

  public boolean isRootNodeShown() {
    return true;
  }

  @NotNull
  public StructureView createStructureView(final FileEditor fileEditor, final Project project) {
    return new StructureViewComponent(fileEditor, createStructureViewModel(), project) {
      public AsyncResult<AbstractTreeNode> expandPathToElement(final Object element) {
        if (element instanceof XmlElement) {
          final XmlElement xmlElement = (XmlElement)element;
          XmlTag tag = PsiTreeUtil.getParentOfType(xmlElement, XmlTag.class, false);
          while (tag != null) {
            final DomElement domElement = DomManager.getDomManager(xmlElement.getProject()).getDomElement(tag);
            if (domElement != null) {
              for (DomElement curElement = domElement; curElement != null; curElement = curElement.getParent()) {
                if (myDescriptor.fun(curElement) == DomService.StructureViewMode.SHOW) {
                  return super.expandPathToElement(curElement.getXmlElement());
                }
              }
            }
            tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class, true);
          }

        }
        return super.expandPathToElement(element);
      }
    };
  }
}