// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlFileTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
*/
public class DomStructureViewTreeModel extends XmlStructureViewTreeModel implements Disposable {
  private final DomElementNavigationProvider myNavigationProvider;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;

  public DomStructureViewTreeModel(@NotNull XmlFile file, @NotNull Function<DomElement, DomService.StructureViewMode> descriptor, @Nullable Editor editor) {
    this(file, DomElementsNavigationManager.getManager(file.getProject()).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME), descriptor, editor);
  }

  public DomStructureViewTreeModel(@NotNull XmlFile file,
                                   final DomElementNavigationProvider navigationProvider,
                                   @NotNull Function<DomElement, DomService.StructureViewMode> descriptor,
                                   @Nullable Editor editor) {
    super(file, editor);
    myNavigationProvider = navigationProvider;
    myDescriptor = descriptor;
  }

  @Override
  public @NotNull StructureViewTreeElement getRoot() {
    XmlFile myFile = getPsiFile();
    final DomFileElement<DomElement> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, DomElement.class);
    return fileElement == null ?
           new XmlFileTreeElement(myFile) :
           new DomStructureTreeElement(fileElement.getRootElement().createStableCopy(), myDescriptor, myNavigationProvider);
  }

  protected DomElementNavigationProvider getNavigationProvider() {
    return myNavigationProvider;
  }

  @Override
  public String toString() {
    return super.toString() + "; file: " + getPsiFile();
  }
}
