/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlStructureViewBuilderFactory implements PsiStructureViewFactory {
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
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
        @NotNull
        public StructureViewModel createStructureViewModel() {
          return new XmlStructureViewTreeModel((XmlFile)psiFile);
        }
      };
    }
    else {
      return null;
    }
  }

  private static XmlStructureViewBuilderProvider[] getStructureViewBuilderProviders() {
    return (XmlStructureViewBuilderProvider[])Extensions.getExtensions(XmlStructureViewBuilderProvider.EXTENSION_POINT_NAME);
  }

  private static StructureViewBuilder getStructureViewBuilderForExtensions(final PsiFile psiFile) {
    for (Language language : ((XMLLanguage)StdLanguages.XML).getLanguageExtensionsForFile(psiFile)) {
      final StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.forLanguage(language).getStructureViewBuilder(psiFile);
      if (builder != null) {
        return builder;
      }
    }
    return null;
  }
}