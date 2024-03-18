// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.GroupItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


class PaletteListPopupStep implements ListPopupStep<ComponentItem>, SpeedSearchFilter<ComponentItem> {
  private final ArrayList<ComponentItem> myItems = new ArrayList<>();
  private final ComponentItem myInitialSelection;
  private final Processor<? super ComponentItem> myRunnable;
  private final @Nls String myTitle;
  private final Project myProject;
  private Runnable myFinalRunnable;

  PaletteListPopupStep(GuiEditor editor,
                       ComponentItem initialSelection,
                       final Processor<? super ComponentItem> runnable,
                       final @Nls String title) {
    myInitialSelection = initialSelection;
    myRunnable = runnable;
    myProject = editor.getProject();
    Palette palette = Palette.getInstance(editor.getProject());
    for(GroupItem group: palette.getToolWindowGroups()) {
      Collections.addAll(myItems, group.getItems());
    }
    myTitle = title;
  }

  @Override
  public @NotNull List<ComponentItem> getValues() {
    return myItems;
  }

  @Override
  public boolean isSelectable(final ComponentItem value) {
    return true;
  }

  @Override
  public Icon getIconFor(final ComponentItem aValue) {
    return aValue.getSmallIcon();
  }

  @Override
  public @NotNull String getTextFor(final ComponentItem value) {
    if (value.isAnyComponent()) {
      return UIDesignerBundle.message("palette.non.palette.component");
    }
    return value.getClassShortName();
  }

  @Override
  public ListSeparator getSeparatorAbove(final ComponentItem value) {
    return null;
  }

  @Override
  public int getDefaultOptionIndex() {
    if (myInitialSelection != null) {
      int index = myItems.indexOf(myInitialSelection);
      if (index >= 0) {
        return index;
      }
    }
    return 0;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public PopupStep onChosen(final ComponentItem selectedValue, final boolean finalChoice) {
    myFinalRunnable = () -> myRunnable.process(selectedValue);
    return PopupStep.FINAL_CHOICE;
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  @Override
  public boolean hasSubstep(final ComponentItem selectedValue) {
    return false;
  }

  @Override
  public void canceled() {
  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  @Override
  public MnemonicNavigationFilter<ComponentItem> getMnemonicNavigationFilter() {
    return null;
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return false;
  }

  @Override
  public SpeedSearchFilter<ComponentItem> getSpeedSearchFilter() {
    return this;
  }

  @Override
  public String getIndexedString(final ComponentItem value) {
    if (value.isAnyComponent()) {
      return "";
    }
    return value.getClassShortName();
  }

  public void hideComponentClass(final String componentClassName) {
    for(ComponentItem item: myItems) {
      if (item.getClassName().equals(componentClassName)) {
        myItems.remove(item);
        break;
      }
    }
  }

  public void hideNonAtomic() {
    for(int i=myItems.size()-1; i >= 0; i--) {
      ComponentItem item = myItems.get(i);
      if (InsertComponentProcessor.getRadComponentFactory(myProject, item.getClassName()) != null || item.getBoundForm() != null) {
        myItems.remove(i);
      }
    }
  }
}
