// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class GroupItem implements Cloneable, PaletteGroup {
  private static final Logger LOG = Logger.getInstance(GroupItem.class);

  public static final DataKey<GroupItem> DATA_KEY = DataKey.create(GroupItem.class.getName());

  @NotNull private String myName;
  @NotNull private final ArrayList<ComponentItem> myItems = new ArrayList<>();
  private boolean myReadOnly = false;
  private boolean mySpecialGroup = false;

  public GroupItem(@NotNull final String name) {
    setName(name);
  }

  public GroupItem(final boolean specialGroup) {
    mySpecialGroup = specialGroup;
  }

  /**
   * @return deep copy of the {@link GroupItem} with copied items.
   */
  @Override
  public GroupItem clone(){
    final GroupItem result = new GroupItem(myName);

    for(ComponentItem myItem : myItems) {
      result.addItem(myItem.clone());
    }

    return result;
  }

  public boolean isReadOnly() {
    return myReadOnly;
  }

  public void setReadOnly(final boolean readOnly) {
    myReadOnly = readOnly;
  }

  @Override
  @NotNull public String getName() {
    if (mySpecialGroup) {
      return UIDesignerBundle.message("palette.special.group");
    }
    return myName;
  }

  @Override
  public String getTabName() {
    return "Swing";
  }

  public void setName(@NotNull final String name){
    myName = name;
  }

  /**
   * @return read-only list of items that belong to the group.
   */
  @Override
  public ComponentItem @NotNull [] getItems() {
    return myItems.toArray(new ComponentItem[0]);
  }

  /** Adds specified {@link ComponentItem} to the group.*/
  public void addItem(@NotNull final ComponentItem item){
    LOG.assertTrue(!myItems.contains(item));

    myItems.add(item);
  }

  /** Replaces specified item with the new one. */
  public void replaceItem(@NotNull final ComponentItem itemToBeReplaced, @NotNull final ComponentItem replacement) {
    LOG.assertTrue(myItems.contains(itemToBeReplaced));

    final int index = myItems.indexOf(itemToBeReplaced);
    myItems.set(index, replacement);
  }

  /** Removed specified {@link ComponentItem} from the group.*/
  public void removeItem(@NotNull final ComponentItem item){
    LOG.assertTrue(myItems.contains(item));

    myItems.remove(item);
  }

  public boolean contains(ComponentItem item) {
    return myItems.contains(item);
  }

  public boolean containsItemClass(@NotNull final String className){
    for(int i = myItems.size() - 1; i >= 0; i--){
      if(className.equals(myItems.get(i).getClassName())){
        return true;
      }
    }

    return false;
  }

  public boolean containsItemCopy(@NotNull final ComponentItem originalItem, final String className) {
    for(int i = myItems.size() - 1; i >= 0; i--){
      if(className.equals(myItems.get(i).getClassName()) && originalItem != myItems.get(i)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @Nullable public ActionGroup getPopupActionGroup() {
    return (ActionGroup) ActionManager.getInstance().getAction("GuiDesigner.PaletteGroupPopupMenu");
  }

  @Override
  public @Nullable Object getData(Project project, @NotNull String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public void handleDrop(Project project, PaletteItem droppedItem, int index) {
    if (droppedItem instanceof ComponentItem componentItem) {
      Palette palette = Palette.getInstance(project);
      int oldIndex = myItems.indexOf(componentItem);
      if (oldIndex >= 0) {
        if (index == -1 || oldIndex == index) return;
        if (oldIndex < index) {
          index--;
        }
        myItems.remove(oldIndex);
      }
      else {
        for(GroupItem groupItem: palette.getGroups()) {
          if (groupItem.myItems.contains(componentItem)) {
            groupItem.removeItem(componentItem);
            break;
          }
        }
      }
      if (index == -1) {
        myItems.add(componentItem);
      }
      else {
        myItems.add(index, componentItem);
      }
      palette.fireGroupsChanged();
    }
  }


  @Override public String toString() {
    return myName;
  }
}
