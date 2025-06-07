// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlStructureViewBuilderFactory implements PsiStructureViewFactory {

  public XmlStructureViewBuilderFactory()
  {
    XmlStructureViewBuilderProvider.EP_NAME.addChangeListener(
      () -> ApplicationManager.getApplication().getMessageBus().syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run(),
      ExtensionPointUtil.createKeyedExtensionDisposable(this, PsiStructureViewFactory.EP_NAME.getPoint()));
  }

  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
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
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new XmlStructureViewTreeModel((XmlFile)psiFile, editor);
      }
    };
  }

  private static XmlStructureViewBuilderProvider[] getStructureViewBuilderProviders() {
    return (XmlStructureViewBuilderProvider[])Extensions.getRootArea()
      .getExtensionPoint(XmlStructureViewBuilderProvider.EP_NAME).getExtensions();
  }

  private static @Nullable StructureViewBuilder getStructureViewBuilderForExtensions(@NotNull PsiFile psiFile) {
    for (Language language : XMLLanguage.INSTANCE.getLanguageExtensionsForFile(psiFile)) {
      PsiStructureViewFactory factory = LanguageStructureViewBuilder.getInstance().forLanguage(language);
      if (factory == null) continue;
      final StructureViewBuilder builder = factory.getStructureViewBuilder(psiFile);
      if (builder != null) {
        return builder;
      }
    }
    return null;
  }
}