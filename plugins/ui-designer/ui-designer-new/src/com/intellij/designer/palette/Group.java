/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class Group implements PaletteGroup {
  private final String myTabName;
  private final String myName;
  private List<Item> myItems = new ArrayList<Item>();

  public Group(String tabName, String name) {
    myTabName = tabName;
    myName = name;
  }

  public void addItem(@NotNull Item item) {
    myItems.add(item);
  }

  @Override
  public PaletteItem[] getItems() {
    return myItems.toArray(new PaletteItem[myItems.size()]);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getTabName() {
    return myTabName;
  }

  @Override
  public ActionGroup getPopupActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Designer.PaletteGroupPopupMenu");
  }

  @Override
  public Object getData(Project project, String dataId) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public void handleDrop(Project project, PaletteItem item, int index) {
    // TODO: Auto-generated method stub
  }
}