package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author max
 */
public class EditChangelistPanel {
  private JTextField myNameTextField;
  private JTextArea myDescriptionTextArea;
  private JPanel myContent;
  @Nullable
  private final ChangeListEditHandler myHandler;
  @NotNull
  private final Consumer<Boolean> myOkEnabledListener;

  public EditChangelistPanel(@Nullable final ChangeListEditHandler handler, @NotNull Consumer<Boolean> okEnabledListener) {
    myHandler = handler;
    myOkEnabledListener = okEnabledListener;

    if (myHandler != null) {
      myNameTextField.addKeyListener(new KeyListener() {
        public void keyTyped(final KeyEvent e) {
          onEditName();
        }
        public void keyPressed(final KeyEvent e) {
        }
        public void keyReleased(final KeyEvent e) {
          onEditName();
        }
      });
      myNameTextField.addInputMethodListener(new InputMethodListener() {
        public void inputMethodTextChanged(final InputMethodEvent event) {
          onEditName();
        }
        public void caretPositionChanged(final InputMethodEvent event) {
        }
      });
      myDescriptionTextArea.addKeyListener(new KeyListener() {
        public void keyTyped(final KeyEvent e) {
        }
        public void keyPressed(final KeyEvent e) {
        }
        public void keyReleased(final KeyEvent e) {
          onEditComment();
        }
      });
      myDescriptionTextArea.addInputMethodListener(new InputMethodListener() {
        public void inputMethodTextChanged(final InputMethodEvent event) {
          onEditComment();
        }
        public void caretPositionChanged(final InputMethodEvent event) {
        }
      });
    }
  }

  void installSupport(Project project) {
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myNameTextField, myDescriptionTextArea);
    }
  }

  private void onEditComment() {
    myNameTextField.setText(myHandler.changeNameOnChangeComment(myNameTextField.getText(), myDescriptionTextArea.getText()));
    enableOk();
  }

  private void onEditName() {
    myDescriptionTextArea.setText(myHandler.changeCommentOnChangeName(myNameTextField.getText(), myDescriptionTextArea.getText()));
    enableOk();
  }

  private void enableOk() {
    myOkEnabledListener.consume(myNameTextField.getText().trim().length() > 0);
  }

  public void setName(String s) {
    myNameTextField.setText(s);
  }

  public String getName() {
    return myNameTextField.getText();
  }

  public void setDescription(String s) {
    myDescriptionTextArea.setText(s);
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  public JComponent getContent() {
    return myContent;
  }

  public void setEnabled(boolean b) {
    UIUtil.setEnabled(myContent, b, true);
  }

  public void requestFocus() {
    myNameTextField.requestFocus();
  }

  public JComponent getPrefferedFocusedComponent() {
    return myNameTextField;
  }

  public void addNameDocumentListener(DocumentListener listener) {
    myNameTextField.getDocument().addDocumentListener(listener);
  }
}
