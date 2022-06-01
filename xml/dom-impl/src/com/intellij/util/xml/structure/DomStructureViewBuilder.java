// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DomStructureViewBuilder extends TreeBasedStructureViewBuilder {
  @NotNull
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;
  @NotNull
  private final XmlFile myFile;

  public DomStructureViewBuilder(@NotNull XmlFile file, @NotNull Function<DomElement,DomService.StructureViewMode> descriptor) {
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
    return new StructureViewComponent(fileEditor, createStructureViewModel(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null), project, true);
  }
}