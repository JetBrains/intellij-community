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

import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public final class Item implements PaletteItem {
  private String myTitle;
  private String myIconPath;
  private Icon myIcon;
  private String myTooltip;

  public Item(String title, String iconPath, String tooltip) {
    myTitle = title;
    myIconPath = iconPath;
    myTooltip = tooltip;
  }

  public String getTitle() {
    return myTitle;
  }

  public Icon getIcon() {
    if (myIcon == null) {
      myIcon = IconLoader.getIcon(myIconPath);
    }
    return myIcon;
  }

  @Override
  public void customizeCellRenderer(ColoredListCellRenderer cellRenderer, boolean selected, boolean hasFocus) {
    cellRenderer.setIcon(getIcon());
    cellRenderer.append(myTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    cellRenderer.setToolTipText(myTooltip);
  }

  @Override
  public DnDDragStartBean startDragging() {
    return null;
  }

  @Override
  public ActionGroup getPopupActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Designer.PaletteItemPopupMenu");
  }

  @Override
  public Object getData(Project project, String dataId) {
    return null;  // TODO: Auto-generated method stub
  }
}