// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class PyInheritedMembersFilter implements FileStructureFilter {
  private static final String ID = "SHOW_INHERITED";

  @Override
  public boolean isReverted() {
    return true;
  }

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof PyStructureViewElement sve) {
      return !sve.isInherited();
    }
    return true;
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(StructureViewBundle.message("action.structureview.show.inherited"),
                                      null,
                                      AllIcons.Hierarchy.Supertypes);
  }

  @Override
  public @NotNull String getCheckBoxText() {
    return StructureViewBundle.message("file.structure.toggle.show.inherited");
  }

  @Override
  public Shortcut @NotNull [] getShortcut() {
    return getActiveKeymapShortcuts("FileStructurePopup").getShortcuts();
  }
}
