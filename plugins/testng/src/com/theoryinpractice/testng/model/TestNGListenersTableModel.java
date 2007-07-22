/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 26, 2005
 * Time: 7:33:45 PM
 */
package com.theoryinpractice.testng.model;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class TestNGListenersTableModel extends AbstractListModel
{

  private List<String> listenerList = new ArrayList<String>();

  public int getSize() {
    return listenerList.size();
  }

  public Object getElementAt(int i) {
    return listenerList.get(i);
  }

  public void setListenerList(List<String> listenerList) {
    this.listenerList.clear();
    this.listenerList.addAll(listenerList);
    fireContentsChanged(this, 0, 0);
  }

  public List<String> getListenerList() {
    return listenerList;
  }

  public void addListener(String listener) {
    listenerList.add(listener);
    fireContentsChanged(this, 0, 0);
  }

  public void removeListener(int rowIndex) {
    listenerList.remove(rowIndex);
    fireContentsChanged(this, 0, 0);
  }
}