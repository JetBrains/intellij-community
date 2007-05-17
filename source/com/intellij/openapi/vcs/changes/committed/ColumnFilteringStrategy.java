package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class ColumnFilteringStrategy extends JPanel implements ChangeListFilteringStrategy {
  private JList myUserList;
  private CopyOnWriteArrayList<ChangeListener> myListeners = new CopyOnWriteArrayList<ChangeListener>();
  private ChangeListColumn<CommittedChangeList> myColumn;

  public ColumnFilteringStrategy(final ChangeListColumn<CommittedChangeList> column) {
    setLayout(new BorderLayout());
    myUserList = new JList();
    add(new JScrollPane(myUserList));
    myUserList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        for(ChangeListener listener: myListeners) {
          listener.stateChanged(new ChangeEvent(this));
        }
      }
    });
    myColumn = column;
  }

  public String toString() {
    return myColumn.getTitle();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(100, 100);
  }

  @Nullable
  public JComponent getFilterUI() {
    return this;
  }

  public void setFilterBase(List<CommittedChangeList> changeLists) {
    final Collection<String> userNames = new TreeSet<String>();
    for(CommittedChangeList changeList: changeLists) {
      userNames.add(myColumn.getValue(changeList).toString());
    }
    final String[] userNameArray = userNames.toArray(new String[userNames.size()]);
    myUserList.setModel(new AbstractListModel() {
      public int getSize() {
        return userNameArray.length+1;
      }

      public Object getElementAt(final int index) {
        if (index == 0) {
          return "All";
        }
        return userNameArray [index-1];
      }
    });
  }

  public void addChangeListener(final ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(final ChangeListener listener) {
    myListeners.remove(listener);
  }

  public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
    final Object[] selection = myUserList.getSelectedValues();
    if (myUserList.getSelectedIndex() == 0 || selection.length == 0) {
      return changeLists;
    }
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    for(CommittedChangeList changeList: changeLists) {
      for(Object userName: selection) {
        if (userName.toString().equals(myColumn.getValue(changeList).toString())) {
          result.add(changeList);
          break;
        }
      }
    }
    return result;
  }
}
