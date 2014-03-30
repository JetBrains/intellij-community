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
package com.jetbrains.python.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyInheritedMembersFilter implements FileStructureFilter {
  private static final String ID = "SHOW_INHERITED";

  @Override
  public boolean isReverted() {
    return true;
  }

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof PyStructureViewElement) {
      final PyStructureViewElement sve = (PyStructureViewElement)treeNode;
      return !sve.isInherited();
    }
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.inherited"),
                                      null,
                                      AllIcons.Hierarchy.Supertypes);
  }

  @NotNull
  @Override
  public String getCheckBoxText() {
    return IdeBundle.message("file.structure.toggle.show.inherited");
  }

  @NotNull
  @Override
  public Shortcut[] getShortcut() {
    return KeymapManager.getInstance().getActiveKeymap().getShortcuts("FileStructurePopup");
  }
}
