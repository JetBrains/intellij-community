// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public class DomStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;
  private final XmlFile myFile;

  public DomStructureViewBuilder(final XmlFile file, final Function<DomElement,DomService.StructureViewMode> descriptor) {
    myFile = file;
    myDescriptor = descriptor;
  }

  @Override
  @NotNull
  public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new DomStructureViewTreeModel(myFile, myDescriptor, editor);
  }

  @Override
  @NotNull
  public StructureView createStructureView(final FileEditor fileEditor, @NotNull final Project project) {
    return new StructureViewComponent(fileEditor, createStructureViewModel(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null), project, true) {
      @Override
      public Promise<AbstractTreeNode> expandPathToElement(final Object element) {
        if (element instanceof XmlElement && ((XmlElement)element).isValid()) {
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