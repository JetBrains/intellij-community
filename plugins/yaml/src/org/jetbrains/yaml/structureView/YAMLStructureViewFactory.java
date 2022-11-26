// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public final class YAMLStructureViewFactory implements PsiStructureViewFactory {
  static final Icon ALIAS_ICON = AllIcons.Nodes.Alias;

  public YAMLStructureViewFactory() {
    YAMLCustomStructureViewFactory.EP_NAME.addChangeListener(
        () -> ApplicationManager.getApplication().getMessageBus().syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run(),
        ExtensionPointUtil.createKeyedExtensionDisposable(this, PsiStructureViewFactory.EP_NAME.getPoint()));
  }

  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof YAMLFile)) {
      return null;
    }

    for (YAMLCustomStructureViewFactory extension : YAMLCustomStructureViewFactory.EP_NAME.getExtensionList()) {
      final StructureViewBuilder builder = extension.getStructureViewBuilder((YAMLFile)psiFile);
      if (builder != null) {
        return builder;
      }
    }

    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new StructureViewModelBase(psiFile, editor, new YAMLStructureViewFile((YAMLFile)psiFile)){
          @Override
          public @NotNull Collection<NodeProvider<?>> getNodeProviders() {
            return Collections.singleton(new YAMLAliasResolveNodeProvider());
          }
        }
          .withSorters(Sorter.ALPHA_SORTER)
          .withSuitableClasses(YAMLFile.class, YAMLDocument.class, YAMLKeyValue.class);
      }
    };
  }

  static @NotNull String getAliasPresentableText(@NotNull YAMLAlias alias) {
    return "*" + alias.getAliasName();
  }

  static @NotNull Collection<StructureViewTreeElement> createChildrenViewTreeElements(@Nullable YAMLPsiElement element, @Nullable String path) {
    if (element == null) {
      return Collections.emptyList();
    }
    Ref<Collection<StructureViewTreeElement>> result = Ref.create(Collections.emptyList());
    element.accept(new YamlPsiElementVisitor() {
      @Override
      public void visitSequence(@NotNull YAMLSequence sequence) {
        result.set(ContainerUtil.map(sequence.getItems(), i -> path == null ? new YAMLStructureViewSequenceItemOriginal(i)
                                                                            : new YAMLStructureViewSequenceItemDuplicated(i, path)));
      }
      @Override
      public void visitMapping(@NotNull YAMLMapping mapping) {
        result.set(ContainerUtil.map(mapping.getKeyValues(), kv -> path == null ? new YAMLStructureViewKeyValueOriginal(kv)
                                                                                : new YAMLStructureViewKeyValueDuplicated(kv, path)));
      }
    });
    return result.get();
  }
}
