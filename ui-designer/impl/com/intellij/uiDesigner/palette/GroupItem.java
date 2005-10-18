package com.intellij.uiDesigner.palette;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;

/**
 * @author Vladimir Kondratyev
 */
public final class GroupItem implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.palette.GroupItem");

  private String myName;
  private final ArrayList<ComponentItem> myItems;

  public GroupItem(final String name) {
    setName(name);
    myItems = new ArrayList<ComponentItem>();
  }

  /**
   * @return deep copy of the {@link GroupItem} with copied items.
   */
  public GroupItem clone(){
    final GroupItem result = new GroupItem(myName);

    for(int i = 0; i < myItems.size(); i++){
      result.addItem(myItems.get(i).clone());
    }

    return result;
  }

  /**
   * @return never <code>null</code>.
   */
  public String getName() {
    return myName;
  }

  /**
   * @param name cannot be <code>null</code>.
   */
  public void setName(final String name){
    LOG.assertTrue(name != null);
    myName = name;
  }

  /**
   * @return read-only list of items that belong to the group.
   * The method never returns <code>null</code>.
   */
  public ArrayList<ComponentItem> getItems() {
    return myItems;
  }

  /** Adds specified {@link ComponentItem} to the group.*/
  public void addItem(final ComponentItem item){
    LOG.assertTrue(item != null);
    LOG.assertTrue(!myItems.contains(item));

    myItems.add(item);
  }

  /** Replaces specified item with the new one. */
  public void replaceItem(final ComponentItem itemToBeReplaced, final ComponentItem replacement){
    LOG.assertTrue(itemToBeReplaced != null);
    LOG.assertTrue(myItems.contains(itemToBeReplaced));
    LOG.assertTrue(replacement != null);

    final int index = myItems.indexOf(itemToBeReplaced);
    myItems.set(index, replacement);
  }

  /** Removed specified {@link ComponentItem} from the group.*/
  public void removeItem(final ComponentItem item){
    LOG.assertTrue(item != null);
    LOG.assertTrue(myItems.contains(item));

    myItems.remove(item);
  }

  public boolean containsItem(final ComponentItem item){
    LOG.assertTrue(item != null);

    for(int i = myItems.size() - 1; i >= 0; i--){
      if(item.equals(myItems.get(i))){
        return true;
      }
    }

    return false;
  }
}
