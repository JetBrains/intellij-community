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
package com.intellij.lang.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlStructureViewBuilderFactory implements PsiStructureViewFactory {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    StructureViewBuilder builder = getStructureViewBuilderForExtensions(psiFile);
    if (builder != null) {
      return builder;
    }

    for (XmlStructureViewBuilderProvider xmlStructureViewBuilderProvider : getStructureViewBuilderProviders()) {
      final StructureViewBuilder structureViewBuilder = xmlStructureViewBuilderProvider.createStructureViewBuilder((XmlFile)psiFile);
      if (structureViewBuilder != null) {
        return structureViewBuilder;
      }
    }

    return new TreeBasedStructureViewBuilder() {
      @Override
      @NotNull
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new XmlStructureViewTreeModel((XmlFile)psiFile, editor);
      }
    };
  }

  private static XmlStructureViewBuilderProvider[] getStructureViewBuilderProviders() {
    return (XmlStructureViewBuilderProvider[])Extensions.getExtensions(XmlStructureViewBuilderProvider.EXTENSION_POINT_NAME);
  }

  @Nullable
  private static StructureViewBuilder getStructureViewBuilderForExtensions(final PsiFile psiFile) {
    for (Language language : XMLLanguage.INSTANCE.getLanguageExtensionsForFile(psiFile)) {
      PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(language);
      if (factory == null) continue;
      final StructureViewBuilder builder = factory.getStructureViewBuilder(psiFile);
      if (builder != null) {
        return builder;
      }
    }
    return null;
  }
}