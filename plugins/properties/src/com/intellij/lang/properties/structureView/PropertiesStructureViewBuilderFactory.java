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
  @NotNull
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
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