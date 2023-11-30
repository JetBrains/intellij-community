// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
  private final @NotNull Function<DomElement, DomService.StructureViewMode> myDescriptor;
  private final @NotNull XmlFile myFile;

  public DomStructureViewBuilder(@NotNull XmlFile file, @NotNull Function<DomElement,DomService.StructureViewMode> descriptor) {
    myFile = file;
    myDescriptor = descriptor;
  }

  @Override
  public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new DomStructureViewTreeModel(myFile, myDescriptor, editor);
  }

  @Override
  public @NotNull StructureView createStructureView(final FileEditor fileEditor, final @NotNull Project project) {
    return new StructureViewComponent(fileEditor, createStructureViewModel(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null), project, true);
  }
}