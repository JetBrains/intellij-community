/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 26, 2005
 * Time: 7:33:45 PM
 */
package com.theoryinpractice.testng.model;

import java.util.ArrayList;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

public class TestNGListenersTableModel extends ListTableModel<String>
{

  private ArrayList<String> listenerList;

  public TestNGListenersTableModel() {
    super(
        new ColumnInfo("Listener Class")
        {
          public Object valueOf(Object object) {
            return object;
          }
        }
    );
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }

  public void setListenerList(ArrayList<String> listenerList) {
    this.listenerList = listenerList;
    setItems(listenerList);
  }

  public void addListener(String listener) {
    listenerList.add(listener);
    setListenerList(listenerList);
  }

  public void removeListener(int rowIndex) {
    listenerList.remove(rowIndex);
    setListenerList(listenerList);
  }
}