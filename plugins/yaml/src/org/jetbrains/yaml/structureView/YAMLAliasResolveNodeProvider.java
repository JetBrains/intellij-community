// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.ActionShortcutProvider;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.resolve.YAMLAliasReference;

import java.util.Collection;
import java.util.Collections;

public class YAMLAliasResolveNodeProvider implements FileStructureNodeProvider<StructureViewTreeElement>, ActionShortcutProvider {
  @NonNls public static final String ID = "YAML_SHOW_RESOLVED_ALIAS_VALUES";

  @NotNull
  @Override
  public String getCheckBoxText() {
    return YAMLBundle.message("YAMLAliasResolveNodeProvider.action.name");
  }

  @NotNull
  @Override
  public Shortcut[] getShortcut() {
    throw new IncorrectOperationException("see getActionIdForShortcut()");
  }

  @NotNull
  @Override
  public String getActionIdForShortcut() {
    return "FileStructurePopup";
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> provideNodes(@NotNull TreeElement node) {
    if (!(node instanceof DuplicatedPsiTreeElementBase)) {
      return Collections.emptyList();
    }
    DuplicatedPsiTreeElementBase yamlNode = (DuplicatedPsiTreeElementBase)node;
    PsiElement psiElem = yamlNode.getElement();
    YAMLPsiElement yamlElem = psiElem instanceof YAMLPsiElement ? (YAMLPsiElement)psiElem : null;
    YAMLValue value = getContainedValue(yamlElem);
    if (!(value instanceof YAMLAlias)) {
      return Collections.emptyList();
    }
    return YAMLStructureViewFactory.createChildrenViewTreeElements(resolveAlias((YAMLAlias)value), yamlNode.getDetails());
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(YAMLBundle.message("YAMLAliasResolveNodeProvider.action.name"),
                                      YAMLBundle.message("YAMLAliasResolveNodeProvider.action.description"),
                                      YAMLStructureViewFactory.ALIAS_ICON);
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }

  @Nullable
  private static YAMLValue resolveAlias(@NotNull YAMLAlias alias) {
    YAMLAliasReference ref = alias.getReference();
    YAMLAnchor anchor = ref == null ? null : ref.resolve();
    return anchor != null ? anchor.getMarkedValue() : null;
  }

  @Nullable
  @Contract("null -> null")
  private static YAMLValue getContainedValue(@Nullable YAMLPsiElement element) {
    if (element == null) {
      return null;
    }
    Ref<YAMLValue> result = Ref.create();
    element.accept(new YamlPsiElementVisitor() {
      @Override
      public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
        result.set(keyValue.getValue());
      }

      @Override
      public void visitSequenceItem(@NotNull YAMLSequenceItem sequenceItem) {
        result.set(sequenceItem.getValue());
      }
    });
    return result.get();
  }
}
