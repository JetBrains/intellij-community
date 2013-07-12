/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.tasks.trello;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloList;
import com.intellij.tasks.trello.model.TrelloModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TrelloRepositoryEditor extends BaseRepositoryEditor<TrelloRepository> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.trello.TrelloRepositoryEditor");

  private static final TrelloBoard UNSPECIFIED_BOARD = new TrelloBoard() {
    @Override
    public String getName() {
      return "-- from all boards --";
    }

    @NotNull
    @Override
    public String getId() {
      return "";
    }
  };

  private final static TrelloList UNSPECIFIED_LIST = new TrelloList() {
    @Override
    public String getName() {
      return "-- from all lists --";
    }

    @NotNull
    @Override
    public String getId() {
      return "";
    }
  };

  private ComboBox myBoardComboBox;
  private ComboBox myListComboBox;
  private JBLabel myListLabel;
  private JBLabel myBoardLabel;

  public TrelloRepositoryEditor(Project project,
                                TrelloRepository repository,
                                Consumer<TrelloRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setVisible(false);
    myURLText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myUserNameText.setVisible(false);
    myPasswordLabel.setText("Token");
    //setAnchor(myPasswordText);

    myPasswordText.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String password = String.valueOf(myPasswordText.getPassword());
        if (password.isEmpty() && password.equals(myRepository.getPassword())) {
          return;
        }
        myRepository.setPassword(password);
        new BoardsDownloader() {
          @Override
          protected List<TrelloBoard> doInBackground() throws Exception {
            myRepository.setCurrentUser(myRepository.fetchUserByToken());
            return super.doInBackground();
          }

          @Override
          protected void done() {
            super.done();
            myBoardComboBox.setSelectedItem(UNSPECIFIED_BOARD);
          }
        }.execute();
        doApply();
      }
    });

    myBoardComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        TrelloBoard board = (TrelloBoard)e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED || board.equals(myRepository.getCurrentBoard())) {
          return;
        }
        if (board != UNSPECIFIED_BOARD) {
          myRepository.setCurrentBoard(board);
          new ListsDownloader() {
            @Override
            protected void done() {
              super.done();
              myListComboBox.setSelectedItem(UNSPECIFIED_LIST);
            }
          }.execute();
        }
        else {
          myRepository.setCurrentBoard(null);
          // will not fire selection event
          myListComboBox.removeAllItems();
          myRepository.setCurrentList(null);
        }
        doApply();
      }
    });
    myBoardComboBox.setRenderer(new TrelloModelRenderer("Set token first"));
    myListComboBox.setRenderer(new TrelloModelRenderer("Select board first"));

    myListComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        // only selection event is considered
        if (e.getStateChange() == ItemEvent.SELECTED) {
          TrelloList list = (TrelloList)e.getItem();
          myRepository.setCurrentList(list != UNSPECIFIED_LIST ? list : null);
          doApply();
        }
      }
    });

    if (myRepository.getCurrentUser() != null) {
      new BoardsDownloader() {
        @Override
        protected void done() {
          // save already selected board, because ItemListener will reset it
          TrelloBoard selectedBoard = myRepository.getCurrentBoard();
          super.done();
          myBoardComboBox.setSelectedItem(selectedBoard != null ? selectedBoard : UNSPECIFIED_BOARD);
        }
      }.execute();
    }
    if (myRepository.getCurrentBoard() != null) {
      new ListsDownloader() {
        @Override
        protected void done() {
          TrelloList selectedList = myRepository.getCurrentList();
          super.done();
          myListComboBox.setSelectedItem(selectedList != null ? selectedList : UNSPECIFIED_LIST);
        }
      }.execute();
    }
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myBoardComboBox = new ComboBox(300);
    myBoardLabel = new JBLabel("Board:", SwingConstants.RIGHT);
    myBoardLabel.setLabelFor(myBoardComboBox);

    myListComboBox = new ComboBox(300);
    myListLabel = new JBLabel("List:", SwingConstants.RIGHT);
    myListLabel.setLabelFor(myListComboBox);
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myBoardLabel, myBoardComboBox)
      .addLabeledComponent(myListLabel, myListComboBox)
      .getPanel();
  }

  private void fillBoardsComboBox(List<TrelloBoard> boards) {
    myBoardComboBox.setModel(new DefaultComboBoxModel(boards.toArray()));
    myBoardComboBox.insertItemAt(UNSPECIFIED_BOARD, 0);
  }

  private void fillListsComboBox(List<TrelloList> lists) {
    myListComboBox.setModel(new DefaultComboBoxModel(lists.toArray()));
    myListComboBox.insertItemAt(UNSPECIFIED_LIST, 0);
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myListLabel.setAnchor(anchor);
    myBoardLabel.setAnchor(anchor);
  }

  private class BoardsDownloader extends SwingWorker<List<TrelloBoard>, Void> {
    @Override
    protected List<TrelloBoard> doInBackground() throws Exception {
      return myRepository.fetchUserBoards();
    }

    @Override
    protected void done() {
      try {
        fillBoardsComboBox(get());
      }
      catch (Exception e) {
        LOG.warn("Error while fetching boards", e);
        myBoardComboBox.removeAllItems();
        myListComboBox.removeAllItems();
      }
    }
  }

  private class ListsDownloader extends SwingWorker<List<TrelloList>, Void> {
    @Override
    protected List<TrelloList> doInBackground() throws Exception {
      return myRepository.fetchBoardLists();
    }

    @Override
    protected void done() {
      try {
        fillListsComboBox(get());
      }
      catch (Exception e) {
        LOG.warn("Error while fetching lists", e);
        myListComboBox.removeAllItems();
      }
    }
  }

  private static class TrelloModelRenderer extends ListCellRendererWrapper<TrelloModel> {
    private String initialMessage;

    private TrelloModelRenderer(String nullDescription) {
      this.initialMessage = nullDescription;
    }

    @Override
    public void customize(JList list, TrelloModel value, int index, boolean selected, boolean hasFocus) {
      setText(value == null ? initialMessage : value.getName());
    }
  }
}
