// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesStructureViewBuilderFactory implements PsiStructureViewFactory {
  @Override
  public @NotNull StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
    PropertiesFileImpl file = (PropertiesFileImpl)psiFile;
    String separator = PropertiesSeparatorManager.getInstance(file.getProject()).getSeparator(file.getResourceBundle());

    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new PropertiesFileStructureViewModel(file, editor, separator);
      }

      @Override
      public @NotNull StructureView createStructureView(@Nullable FileEditor fileEditor,
                                                        @NotNull Project project,
                                                        StructureViewModel model) {
        return new PropertiesFileStructureViewComponent(project, fileEditor, (PropertiesFileStructureViewModel)model);
      }
    };
  }
}