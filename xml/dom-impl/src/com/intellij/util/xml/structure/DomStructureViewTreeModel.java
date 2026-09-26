// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * @author Gregory.Shrago
*/
public class DomStructureViewTreeModel extends TextEditorBasedStructureViewModel implements Disposable {
  private final DomElementNavigationProvider myNavigationProvider;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;

  public DomStructureViewTreeModel(@NotNull XmlFile file, @NotNull Function<DomElement, DomService.StructureViewMode> descriptor, @Nullable Editor editor) {
    this(file, DomElementsNavigationManager.getManager(file.getProject()).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME), descriptor, editor);
  }

  public DomStructureViewTreeModel(@NotNull XmlFile file,
                                    final DomElementNavigationProvider navigationProvider,
                                    @NotNull Function<DomElement, DomService.StructureViewMode> descriptor,
                                    @Nullable Editor editor) {
    super(editor, file);
    myNavigationProvider = navigationProvider;
    myDescriptor = descriptor;
  }

  @Override
  public @NotNull StructureViewTreeElement getRoot() {
    XmlFile myFile = (XmlFile)getPsiFile();
    final DomFileElement<DomElement> fileElement = DomManager.getDomManager(myFile.getProject()).getFileElement(myFile, DomElement.class);
    return fileElement == null ?
           new XmlFileStructureRootElement(myFile) :
           new DomStructureTreeElement(fileElement.getRootElement().createStableCopy(), myDescriptor, myNavigationProvider);
  }

  @Override
  public String toString() {
    return super.toString() + "; file: " + getPsiFile();
  }

  private static final class XmlFileStructureRootElement implements StructureViewTreeElement {
    private final XmlFile myFile;

    private XmlFileStructureRootElement(@NotNull XmlFile file) {
      myFile = file;
    }

    @Override
    public Object getValue() {
      return myFile;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      ItemPresentation presentation = myFile.getPresentation();
      return presentation == null ? new XmlFilePresentation(myFile) : presentation;
    }

    @Override
    public StructureViewTreeElement @NotNull [] getChildren() {
      return EMPTY_ARRAY;
    }

    @Override
    public void navigate(boolean requestFocus) {
      myFile.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return myFile.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return myFile.canNavigateToSource();
    }
  }

  private static final class XmlFilePresentation implements ItemPresentation {
    private final XmlFile myFile;

    private XmlFilePresentation(@NotNull XmlFile file) {
      myFile = file;
    }

    @Override
    public @NotNull String getPresentableText() {
      return myFile.getName();
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
      return myFile.getIcon(0);
    }
  }
}
