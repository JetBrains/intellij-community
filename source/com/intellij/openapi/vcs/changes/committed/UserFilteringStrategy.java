package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ChangeEvent;
import java.util.List;
import java.util.TreeSet;
import java.util.Collection;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.awt.*;

/**
 * @author yole
 */
public class UserFilteringStrategy extends JPanel implements ChangeListFilteringStrategy {
  private JList myUserList;
  private CopyOnWriteArrayList<ChangeListener> myListeners = new CopyOnWriteArrayList<ChangeListener>();

  public UserFilteringStrategy() {
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
  }

  public String toString() {
    return "User";
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
      userNames.add(changeList.getCommitterName());
    }
    final String[] userNameArray = userNames.toArray(new String[userNames.size()]);
    myUserList.setModel(new AbstractListModel() {
      public int getSize() {
        return userNameArray.length+1;
      }

      public Object getElementAt(final int index) {
        if (index == 0) {
          return "All Users";
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
        if (userName.toString().equals(changeList.getCommitterName())) {
          result.add(changeList);
          break;
        }
      }
    }
    return result;
  }
}
