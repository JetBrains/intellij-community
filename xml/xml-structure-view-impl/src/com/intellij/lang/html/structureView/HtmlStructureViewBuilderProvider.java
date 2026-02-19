// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html.structureView;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {
  @Override
  public @Nullable StructureViewBuilder createStructureViewBuilder(final @NotNull XmlFile file) {
    if (file.getViewProvider().getFileType() != HtmlFileType.INSTANCE) return null;

    return new TreeBasedStructureViewBuilder() {
      @Override
      public boolean isRootNodeShown() {
        return false;
      }

      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new HtmlStructureViewTreeModel(file, editor);
      }
    };
  }
}