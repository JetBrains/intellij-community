// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
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
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.resolve.YAMLAliasReference;

import java.util.Collection;
import java.util.Collections;

public class YAMLAliasResolveNodeProvider implements FileStructureNodeProvider<StructureViewTreeElement>, ActionShortcutProvider {
  public static final @NonNls String ID = "YAML_SHOW_RESOLVED_ALIAS_VALUES";

  @Override
  public @NotNull String getCheckBoxText() {
    return YAMLBundle.message("YAMLAliasResolveNodeProvider.action.name");
  }

  @Override
  public Shortcut @NotNull [] getShortcut() {
    throw new IncorrectOperationException("see getActionIdForShortcut()");
  }

  @Override
  public @NotNull String getActionIdForShortcut() {
    return "FileStructurePopup";
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> provideNodes(@NotNull TreeElement node) {
    PsiElement psiElem;
    String details;
    if (node instanceof DuplicatedPsiTreeElementBase yamlNode) {
      psiElem = yamlNode.getElement();
      details = yamlNode.getDetails();
    }
    else if (node instanceof PsiTreeElementBase yamlNode) {
      psiElem = yamlNode.getElement();
      if (psiElem == null) {
        // not sure it is possible
        return Collections.emptyList();
      }
      details = calculateStartPath(psiElem);
    }
    else {
      return Collections.emptyList();
    }
    YAMLPsiElement yamlElem = psiElem instanceof YAMLPsiElement ? (YAMLPsiElement)psiElem : null;
    YAMLValue value = getContainedValue(yamlElem);
    if (!(value instanceof YAMLAlias)) {
      return Collections.emptyList();
    }
    return YAMLStructureViewFactory.createChildrenViewTreeElements(resolveAlias((YAMLAlias)value), details);
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(YAMLBundle.message("YAMLAliasResolveNodeProvider.action.name"),
                                      YAMLBundle.message("YAMLAliasResolveNodeProvider.action.description"),
                                      YAMLStructureViewFactory.ALIAS_ICON);
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

  private static @Nullable YAMLValue resolveAlias(@NotNull YAMLAlias alias) {
    YAMLAliasReference ref = alias.getReference();
    YAMLAnchor anchor = ref == null ? null : ref.resolve();
    return anchor != null ? anchor.getMarkedValue() : null;
  }

  @Contract("null -> null")
  private static @Nullable YAMLValue getContainedValue(@Nullable YAMLPsiElement element) {
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

  private static @NotNull String calculateStartPath(@NotNull PsiElement psiElem) {
    if (!(psiElem instanceof YAMLPsiElement)) {
      return "";
    }
    return YAMLUtil.getConfigFullName((YAMLPsiElement)psiElem);
  }
}
