/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 14:15:18
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

public class MultipleChangeListBrowser extends ChangesBrowser {
  private ChangeListChooser myChangeListChooser;
  private ChangeListListener myChangeListListener = new MyChangeListListener();
  private boolean myShowingAllChangeLists;
  private EventDispatcher<SelectedListChangeListener> myDispatcher = EventDispatcher.create(SelectedListChangeListener.class);
  private Collection<Change> myAllChanges;
  private Map<Change, LocalChangeList> myChangeListsMap;

  public MultipleChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                   final ChangeList initialListSelection,
                                   final boolean showChangelistChooser,
                                   final boolean capableOfExcludingChanges) {
    super(project, changeLists, changes, initialListSelection, showChangelistChooser, capableOfExcludingChanges);

    myChangeListChooser = new ChangeListChooser(changeLists);
    myHeaderPanel.add(myChangeListChooser, BorderLayout.EAST);
    myShowingAllChangeLists = changeLists.equals(ChangeListManager.getInstance(project).getChangeLists());
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);
  }

  @Override
  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    myAllChanges = new ArrayList<Change>();

    for (ChangeList list : changeLists) {
      if (list instanceof LocalChangeList) {
        myAllChanges.addAll(list.getChanges());
        if (initialListSelection == null) {
          for(Change c: list.getChanges()) {
            if (changes.contains(c)) {
              mySelectedChangeList = list;
              break;
            }
          }
        }
      }
    }

    if (mySelectedChangeList == null) {
      for(ChangeList list: changeLists) {
        if (list instanceof LocalChangeList && ((LocalChangeList) list).isDefault()) {
          mySelectedChangeList = list;
          break;
        }
      }
      if (mySelectedChangeList == null && !changeLists.isEmpty()) {
        mySelectedChangeList = changeLists.get(0);
      }
    }
  }

  @Override
  public void dispose() {
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListListener);
  }

  public Collection<Change> getAllChanges() {
    return myAllChanges;
  }

  public interface SelectedListChangeListener extends EventListener {
    void selectedListChanged();
  }

  public void addSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.removeListener(listener);
  }

  protected void setSelectedList(final ChangeList list) {
    mySelectedChangeList = list;
    rebuildList();
    myDispatcher.getMulticaster().selectedListChanged();
  }

  @Override
  protected void rebuildList() {
    if (myChangesToDisplay == null) {
      final ChangeListManager manager = ChangeListManager.getInstance(myProject);
      myChangeListsMap = new HashMap<Change, LocalChangeList>();
      for (Change change : myAllChanges) {
        myChangeListsMap.put(change, manager.getChangeList(change));
      }
    }

    super.rebuildList();
  }

  @Override
  public List<Change> getCurrentDisplayedChanges() {
    if (myChangesToDisplay == null) {
      return sortChanges(filterBySelectedChangeList(myAllChanges));
    }
    return super.getCurrentDisplayedChanges();
  }

  public List<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myViewer.getIncludedChanges());
  }

  private List<Change> filterBySelectedChangeList(final Collection<Change> changes) {
    List<Change> filtered = new ArrayList<Change>();
    for (Change change : changes) {
      if (myReadOnly || getList(change) == mySelectedChangeList) {
        filtered.add(change);
      }
    }
    return filtered;
  }

  private ChangeList getList(final Change change) {
    return myChangeListsMap.get(change);
  }

  private class ChangeListChooser extends JPanel {
    private JComboBox myChooser;

    public ChangeListChooser(List<? extends ChangeList> lists) {
      super(new BorderLayout());
      myChooser = new JComboBox();
      myChooser.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final LocalChangeList l = ((LocalChangeList)value);
          append(l.getName(),
                 l.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });

      myChooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            setSelectedList((LocalChangeList)myChooser.getSelectedItem());
          }
        }
      });

      updateLists(lists);
      myChooser.setEditable(false);
      add(myChooser, BorderLayout.EAST);

      JLabel label = new JLabel(VcsBundle.message("commit.dialog.changelist.label"));
      label.setDisplayedMnemonic('l');
      label.setLabelFor(myChooser);
      add(label, BorderLayout.CENTER);
    }

    public void updateLists(List<? extends ChangeList> lists) {
      myChooser.setModel(new DefaultComboBoxModel(lists.toArray()));
      myChooser.setEnabled(lists.size() > 1);
      myChooser.setSelectedItem(mySelectedChangeList);
    }
  }

  private class MyChangeListListener implements ChangeListListener {
    public void changeListAdded(ChangeList list) {
      if (myChangeListChooser != null && myShowingAllChangeLists) {
        myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeLists());
      }
    }

    public void changeListRemoved(ChangeList list) {
    }

    public void changeListChanged(ChangeList list) {
    }

    public void changeListRenamed(ChangeList list, String oldName) {
    }

    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
    }

    public void defaultListChanged(ChangeList newDefaultList) {
    }

    public void changeListUpdateDone() {
    }
  }
}
