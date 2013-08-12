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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloList;
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
    @NotNull
    @Override
    public String getName() {
      return "-- from all boards --";
    }
  };

  private final static TrelloList UNSPECIFIED_LIST = new TrelloList() {
    @NotNull
    @Override
    public String getName() {
      return "-- from all lists --";
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
        if (password.equals(myRepository.getPassword())) {
          return;
        }
        myRepository.setPassword(password);
        new BoardsDownloader(UNSPECIFIED_BOARD) {
          @Override
          protected List<TrelloBoard> download() throws Exception {
            myRepository.setCurrentUser(myRepository.fetchUserByToken());
            return super.download();
          }
        }.runOnPooledThread();
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
          new ListsDownloader(UNSPECIFIED_LIST).runOnPooledThread();
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
    myBoardComboBox.setRenderer(new TrelloBoardRenderer("Set token first"));
    myListComboBox.setRenderer(new TrelloListRenderer("Select board first"));

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

    // Initial setup:
    if (myRepository.getCurrentUser() != null) {
      new BoardsDownloader(myRepository.getCurrentBoard()) {
        @Override
        protected List<TrelloBoard> download() throws Exception {
          List<TrelloBoard> boards = super.download();
          if (myBoard == null) {
            return boards;
          }
          int i = boards.indexOf(myBoard);
          // update information about selected board
          // if it's open and thus downloaded with other boards of user, take info from there,
          // otherwise issue a separate request
          myBoard = i >= 0 ? boards.get(i) : myRepository.fetchBoardById(myBoard.getId());
          myRepository.setCurrentBoard(myBoard);
          return boards;
        }
      }.runOnPooledThread();
    }

    if (myRepository.getCurrentBoard() != null) {
      new ListsDownloader(myRepository.getCurrentList()) {
        @Override
        protected List<TrelloList> download() throws Exception {
          List<TrelloList> lists = super.download();
          if (myList == null) {
            return lists;
          }
          int i = lists.indexOf(myList);
          myList = i >= 0 ? lists.get(i) : myRepository.fetchListById(myList.getId());
          TrelloBoard currentBoard = myRepository.getCurrentBoard();
          if (currentBoard != null && !myList.getIdBoard().equals(currentBoard.getId())) {
            myList.setMoved(true);
          }
          myRepository.setCurrentList(myList);
          return lists;
        }
      }.runOnPooledThread();
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

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myListLabel.setAnchor(anchor);
    myBoardLabel.setAnchor(anchor);
  }


  private abstract class Downloader<T> implements Runnable {

    private final ModalityState myModalityState = ModalityState.current();

    protected abstract T download() throws Exception;

    protected void updateUI(T result) {
      // empty
    }

    protected void handleException(Exception e) {
      // empty
    }

    @Override
    public void run() {
      try {
        final T result;
        synchronized (myRepository) {
          result = download();
        }
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            updateUI(result);
          }
        }, myModalityState);
      }
      catch (final Exception e) {
        LOG.warn(e);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            handleException(e);
          }
        }, myModalityState);
      }
    }

    public void runOnPooledThread() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
    }
  }

  private class BoardsDownloader extends Downloader<List<TrelloBoard>> {
    protected TrelloBoard myBoard;

    private BoardsDownloader(TrelloBoard selectedBoard) {
      myBoard = selectedBoard;
    }

    @Override
    protected List<TrelloBoard> download() throws Exception {
      return myRepository.fetchUserBoards();
    }

    @Override
    protected void updateUI(List<TrelloBoard> boards) {
      myBoardComboBox.setModel(new DefaultComboBoxModel(boards.toArray()));
      myBoardComboBox.insertItemAt(UNSPECIFIED_BOARD, 0);
      // explicitly add missing closed board
      if (!(myBoard == null || myBoard == UNSPECIFIED_BOARD) && !boards.contains(myBoard)) {
        myBoardComboBox.addItem(myBoard);
      }
      myBoardComboBox.setSelectedItem(myBoard == null ? UNSPECIFIED_BOARD : myBoard);
    }

    @Override
    protected void handleException(Exception e) {
      myBoardComboBox.removeAllItems();
      myListComboBox.removeAllItems();
    }
  }

  private class ListsDownloader extends Downloader<List<TrelloList>> {
    protected TrelloList myList;

    private ListsDownloader(TrelloList selectedList) {
      this.myList = selectedList;
    }

    @Override
    protected List<TrelloList> download() throws Exception {
      return myRepository.fetchBoardLists();
    }

    @Override
    protected void updateUI(List<TrelloList> lists) {
      myListComboBox.setModel(new DefaultComboBoxModel(lists.toArray()));
      myListComboBox.insertItemAt(UNSPECIFIED_LIST, 0);
      // explicitly add moved or archived list to combobox: see IDEA-111819 for details
      if (!(myList == null || myList == UNSPECIFIED_LIST) && !lists.contains(myList)) {
        myListComboBox.addItem(myList);
      }
      myListComboBox.setSelectedItem(myList == null ? UNSPECIFIED_LIST : myList);
    }

    @Override
    protected void handleException(Exception e) {
      myListComboBox.removeAllItems();
    }
  }

  private static class TrelloBoardRenderer extends ListCellRendererWrapper<TrelloBoard> {
    private String myNullDescription;

    private TrelloBoardRenderer(String nullDescription) {
      this.myNullDescription = nullDescription;
    }

    @Override
    public void customize(JList list, TrelloBoard board, int index, boolean selected, boolean hasFocus) {
      if (board == null) {
        setText(myNullDescription);
        return;
      }
      setText(board.isClosed() ? board.getName() + " (closed)" : board.getName());
    }
  }

  private static class TrelloListRenderer extends ListCellRendererWrapper<TrelloList> {
    private String myNullDescription;

    private TrelloListRenderer(String nullDescription) {
      this.myNullDescription = nullDescription;
    }

    @Override
    public void customize(JList list, TrelloList trelloList, int index, boolean selected, boolean hasFocus) {
      if (trelloList == null) {
        setText(myNullDescription);
        return;
      }
      String text = trelloList.getName();
      if (trelloList.isClosed() && trelloList.isMoved()) {
        text += " (archived,moved)";
      }
      else if (trelloList.isMoved()) {
        text += " (moved)";
      }
      else if (trelloList.isClosed()) {
        text += " (archived)";
      }
      setText(text);
    }
  }
}
