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
package com.intellij.designer.palette2;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PalettePanel extends JPanel {
  private final JPanel myPaletteContainer = new PaletteContainer();
  private List<PaletteGroup> myGroups = Collections.emptyList();

  public PalettePanel() {
    super(new GridLayout(1, 1));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPaletteContainer);
    scrollPane.setBorder(null);
    add(scrollPane);
  }

  public PaletteItem getActiveItem() {
    // XXX
    return null;
  }

  public void clearActiveItem() {
    // XXX
  }

  public boolean isEmpty() {
    return myGroups.isEmpty();
  }

  public void loadPalette(@Nullable DesignerEditorPanel designer) {
    myGroups = designer.getPaletteGroups();
    myPaletteContainer.removeAll();

    for (PaletteGroup group : myGroups) {
      PaletteGroupComponent groupComponent = new PaletteGroupComponent(group);
      PaletteItemsComponent itemsComponent = new PaletteItemsComponent(group);

      groupComponent.setItemsComponent(itemsComponent);
      myPaletteContainer.add(groupComponent);
      myPaletteContainer.add(itemsComponent);
    }

    myPaletteContainer.revalidate();
  }
}