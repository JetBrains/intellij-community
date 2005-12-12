package com.intellij.uiDesigner.palette;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Vladimir Kondratyev
 */
public final class GroupItem implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.GroupItem");

  @NotNull private String myName;
  @NotNull private final ArrayList<ComponentItem> myItems;

  public GroupItem(@NotNull final String name) {
    setName(name);
    myItems = new ArrayList<ComponentItem>();
  }

  /**
   * @return deep copy of the {@link GroupItem} with copied items.
   */
  public GroupItem clone(){
    final GroupItem result = new GroupItem(myName);

    for(ComponentItem myItem : myItems) {
      result.addItem(myItem.clone());
    }

    return result;
  }

  @NotNull public String getName() {
    return myName;
  }

  public void setName(@NotNull final String name){
    myName = name;
  }

  /**
   * @return read-only list of items that belong to the group.
   */
  @NotNull public ArrayList<ComponentItem> getItems() {
    return myItems;
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
}
