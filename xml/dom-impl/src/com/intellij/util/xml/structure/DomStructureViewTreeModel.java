/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  @NotNull
  public StructureViewTreeElement getRoot() {
    XmlFile myFile = getPsiFile();
    final DomFileElement<DomElement> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, DomElement.class);
    return fileElement == null?
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
