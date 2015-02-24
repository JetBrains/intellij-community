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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloList;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
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
  private static final Logger LOG = Logger.getInstance(TrelloRepositoryEditor.class);

  private ComboBox myBoardComboBox;
  private ComboBox myListComboBox;
  private JBLabel myListLabel;
  private JBLabel myBoardLabel;
  private JBCheckBox myAllCardsCheckBox;

  public TrelloRepositoryEditor(Project project,
                                TrelloRepository repository,
                                Consumer<TrelloRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setVisible(false);
    myURLText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myUserNameText.setVisible(false);
    myPasswordLabel.setText("Token:");
    myAllCardsCheckBox.setSelected(myRepository.isIncludeAllCards());
    //setAnchor(myPasswordText);

    myPasswordText.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String password = String.valueOf(myPasswordText.getPassword());
        if (password.isEmpty() || password.equals(myRepository.getPassword())) {
          return;
        }
        myRepository.setPassword(password);
        new BoardsComboBoxUpdater() {
          @Override
          @NotNull
          protected List<TrelloBoard> fetch(@NotNull ProgressIndicator indicator) throws Exception {
            myRepository.setCurrentUser(myRepository.fetchUserByToken());
            return super.fetch(indicator);
          }
        }.queue();
        doApply();
      }
    });

    myBoardComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        final TrelloBoard board = (TrelloBoard)e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED || board.equals(myRepository.getCurrentBoard())) {
          return;
        }
        if (board != TrelloRepository.UNSPECIFIED_BOARD) {
          myRepository.setCurrentBoard(board);
          new ListsComboBoxUpdater() {
            @Nullable
            @Override
            public TrelloList getSelectedItem() {
              return TrelloRepository.UNSPECIFIED_LIST;
            }
          }.queue();
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
    myBoardComboBox.setRenderer(new TaskUiUtil.SimpleComboBoxRenderer<TrelloBoard>("Set token first") {
      @NotNull
      @Override
      protected String getDescription(@NotNull TrelloBoard board) {
        return board.isClosed() ? board.getName() + " (closed)" : board.getName();
      }
    });

    myListComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        // only selection event is considered
        if (e.getStateChange() == ItemEvent.SELECTED) {
          final TrelloList list = (TrelloList)e.getItem();
          myRepository.setCurrentList(list);
          doApply();
        }
      }
    });
    myListComboBox.setRenderer(new TaskUiUtil.SimpleComboBoxRenderer<TrelloList>("Select board first") {
      @NotNull
      @Override
      protected String getDescription(@NotNull TrelloList list) {
        String text = list.getName();
        if (list.isClosed() && list.isMoved()) {
          text += " (archived,moved)";
        }
        else if (list.isMoved()) {
          text += " (moved)";
        }
        else if (list.isClosed()) {
          text += " (archived)";
        }
        return text;
      }
    });

    installListener(myAllCardsCheckBox);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        initialize();
      }
    });
  }

  private void initialize() {
    if (myRepository.getCurrentUser() != null) {
      new BoardsComboBoxUpdater() {
        @Override
        @NotNull
        protected List<TrelloBoard> fetch(@NotNull ProgressIndicator indicator) throws Exception {
          final List<TrelloBoard> boards = super.fetch(indicator);
          TrelloBoard currentBoard = getSelectedItem();
          if (currentBoard != null && currentBoard != TrelloRepository.UNSPECIFIED_BOARD) {
            final int i = boards.indexOf(currentBoard);
            // update information about selected board
            // if it's open and thus downloaded with other boards of user, take info from there,
            // otherwise issue a separate request
            currentBoard = i >= 0 ? boards.get(i) : myRepository.fetchBoardById(currentBoard.getId());
            myRepository.setCurrentBoard(currentBoard);
          }
          return boards;
        }
      }.queue();
    }

    if (myRepository.getCurrentBoard() != null && myRepository.getCurrentBoard() != TrelloRepository.UNSPECIFIED_BOARD) {
      new ListsComboBoxUpdater() {
        @Override
        @NotNull
        protected List<TrelloList> fetch(@NotNull ProgressIndicator indicator) throws Exception {
          final List<TrelloList> lists = super.fetch(indicator);
          TrelloList currentList = myRepository.getCurrentList();
          if (currentList != null && currentList != TrelloRepository.UNSPECIFIED_LIST) {
            final int i = lists.indexOf(currentList);
            currentList = i >= 0 ? lists.get(i) : myRepository.fetchListById(currentList.getId());
            final TrelloBoard currentBoard = myRepository.getCurrentBoard();
            if (currentBoard != null && !currentList.getIdBoard().equals(currentBoard.getId())) {
              currentList.setMoved(true);
            }
            myRepository.setCurrentList(currentList);
          }
          return lists;
        }
      }.queue();
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

    myAllCardsCheckBox = new JBCheckBox("Include cards not assigned to me");

    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myBoardLabel, myBoardComboBox)
      .addLabeledComponent(myListLabel, myListComboBox)
      .addComponentToRightColumn(myAllCardsCheckBox)
      .getPanel();
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setIncludeAllCards(myAllCardsCheckBox.isSelected());
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myListLabel.setAnchor(anchor);
    myBoardLabel.setAnchor(anchor);
  }


  private class BoardsComboBoxUpdater extends TaskUiUtil.ComboBoxUpdater<TrelloBoard> {
    public BoardsComboBoxUpdater() {
      super(TrelloRepositoryEditor.this.myProject, "Downloading Trello boards...", myBoardComboBox);
    }

    @NotNull
    @Override
    protected List<TrelloBoard> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      return myRepository.fetchUserBoards();
    }

    @Nullable
    @Override
    public TrelloBoard getExtraItem() {
      return TrelloRepository.UNSPECIFIED_BOARD;
    }

    @Nullable
    @Override
    public TrelloBoard getSelectedItem() {
      return myRepository.getCurrentBoard();
    }

    @Override
    protected void handleError() {
      super.handleError();
      myListComboBox.removeAllItems();
    }

    @Override
    protected boolean addSelectedItemIfMissing() {
      return true;
    }
  }

  private class ListsComboBoxUpdater extends TaskUiUtil.ComboBoxUpdater<TrelloList> {
    public ListsComboBoxUpdater() {
      super(TrelloRepositoryEditor.this.myProject, "Downloading Trello lists...", myListComboBox);
    }

    @NotNull
    @Override
    protected List<TrelloList> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      return myRepository.fetchBoardLists();
    }

    @Nullable
    @Override
    public TrelloList getExtraItem() {
      return TrelloRepository.UNSPECIFIED_LIST;
    }

    @Nullable
    @Override
    public TrelloList getSelectedItem() {
      return myRepository.getCurrentList();
    }

    @Override
    protected boolean addSelectedItemIfMissing() {
      return true;
    }
  }
}
