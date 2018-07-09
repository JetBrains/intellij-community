package org.jetbrains.yaml.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author oleg
 */
public class YAMLStructureViewFactory implements PsiStructureViewFactory {
  static final Icon ALIAS_ICON = AllIcons.Nodes.ExpandNode;

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof YAMLFile)) {
      return null;
    }

    for (YAMLCustomStructureViewFactory extension : YAMLCustomStructureViewFactory.EP_NAME.getExtensions()) {
      final StructureViewBuilder builder = extension.getStructureViewBuilder((YAMLFile)psiFile);
      if (builder != null) {
        return builder;
      }
    }

    return new TreeBasedStructureViewBuilder() {
      @Override
      @NotNull
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new StructureViewModelBase(psiFile, editor, new YAMLStructureViewFile((YAMLFile)psiFile)){
          @NotNull
          @Override
          public Collection<NodeProvider> getNodeProviders() {
            return Collections.singleton(new YAMLAliasResolveNodeProvider());
          }
        }
          .withSorters(Sorter.ALPHA_SORTER)
          .withSuitableClasses(YAMLFile.class, YAMLDocument.class, YAMLKeyValue.class);
      }
    };
  }

  @NotNull
  static String getAliasPresentableText(@NotNull YAMLAlias alias) {
    return "*" + alias.getAliasName();
  }

  @NotNull
  static Collection<StructureViewTreeElement> createChildrenViewTreeElements(@Nullable YAMLPsiElement element, String path) {
    if (element == null) {
      return Collections.emptyList();
    }
    Ref<Collection<StructureViewTreeElement>> result = Ref.create(Collections.emptyList());
    element.accept(new YamlPsiElementVisitor() {
      @Override
      public void visitSequence(@NotNull YAMLSequence sequence) {
        result.set(ContainerUtil.map(sequence.getItems(), i -> new YAMLStructureViewSequenceItem(i, path)));
      }
      @Override
      public void visitMapping(@NotNull YAMLMapping mapping) {
        result.set(ContainerUtil.map(mapping.getKeyValues(), kv -> new YAMLStructureViewKeyValue(kv, path)));
      }
    });
    return result.get();
  }
}
