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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

public class MultipleChangeListBrowser extends ChangesBrowser {
  private final ChangeListChooser myChangeListChooser;
  private final ChangeListListener myChangeListListener = new MyChangeListListener();
  private final boolean myShowingAllChangeLists;
  private final EventDispatcher<SelectedListChangeListener> myDispatcher = EventDispatcher.create(SelectedListChangeListener.class);
  private Collection<Change> myAllChanges;
  private Map<Change, LocalChangeList> myChangeListsMap;

  public MultipleChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                   final ChangeList initialListSelection,
                                   final boolean capableOfExcludingChanges,
                                   final boolean highlightProblems) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems);

    myChangeListChooser = new ChangeListChooser(changeLists);
    myHeaderPanel.add(myChangeListChooser, BorderLayout.EAST);
    myShowingAllChangeLists = changeLists.equals(ChangeListManager.getInstance(project).getChangeLists());
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);
  }

  @Override
  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    myAllChanges = new ArrayList<Change>();
    mySelectedChangeList = initialListSelection;

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

  private void setSelectedList(final ChangeList list) {
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

  @NotNull
  public List<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myViewer.getIncludedChanges());
  }

  private List<Change> filterBySelectedChangeList(final Collection<Change> changes) {
    List<Change> filtered = new ArrayList<Change>();
    for (Change change : changes) {
      if (getList(change) == mySelectedChangeList) {
        filtered.add(change);
      }
    }
    return filtered;
  }

  private ChangeList getList(final Change change) {
    return myChangeListsMap.get(change);
  }

  @Override
  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    final MoveChangesToAnotherListAction moveAction = new MoveChangesToAnotherListAction() {
      public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);
        rebuildList();
      }
    };

    moveAction.registerCustomShortcutSet(CommonShortcuts.getMove(), myViewer);
    toolBarGroup.add(moveAction);
  }

  @Override
  protected List<AnAction> createDiffActions(final Change change) {
    List<AnAction> actions = super.createDiffActions(change);
    actions.add(new MoveAction(change));
    return actions;
  }

  private class ChangeListChooser extends JPanel {
    private final JComboBox myChooser;

    public ChangeListChooser(List<? extends ChangeList> lists) {
      super(new BorderLayout());
      myChooser = new JComboBox() {
        public Dimension getMinimumSize() {
          return new Dimension(0, 0);
        }
      };
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
      add(myChooser, BorderLayout.CENTER);

      JLabel label = new JLabel(VcsBundle.message("commit.dialog.changelist.label"));
      label.setDisplayedMnemonic('l');
      label.setLabelFor(myChooser);
      add(label, BorderLayout.WEST);
    }

    public void updateLists(List<? extends ChangeList> lists) {
      myChooser.setModel(new DefaultComboBoxModel(lists.toArray()));
      myChooser.setEnabled(lists.size() > 1);
      myChooser.setSelectedItem(mySelectedChangeList);
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {
    public void changeListAdded(ChangeList list) {
      Runnable runnable = new Runnable() {
        public void run() {
          if (myChangeListChooser != null && myShowingAllChangeLists) {
            myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeLists());
          }
        }
      };
      if (SwingUtilities.isEventDispatchThread()) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(MultipleChangeListBrowser.this));
      }
    }
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    private final Change myChange;

    public MoveAction(final Change change) {
      myChange = change;
    }

    public void actionPerformed(AnActionEvent e) {
      askAndMove(myProject, new Change[]{myChange}, null);
    }
  }
}
