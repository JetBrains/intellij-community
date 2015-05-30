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
  @Nullable
  public StructureViewBuilder createStructureViewBuilder(@NotNull final XmlFile file) {
    if (file.getViewProvider().getFileType() != HtmlFileType.INSTANCE) return null;

    return new TreeBasedStructureViewBuilder() {
      @Override
      public boolean isRootNodeShown() {
        return false;
      }

      @Override
      @NotNull
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new HtmlStructureViewTreeModel(file, editor);
      }
    };
  }
}