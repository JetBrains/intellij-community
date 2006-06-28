package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.net.HTTPProxySettingsDialog;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.Keymap;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 8, 2003
 * Time: 3:49:50 PM
 * To change this template use Options | File Templates.
 */
public class EAPSendErrorDialog extends DialogWrapper {
  private JTextField myItnLoginTextField;
  private JPasswordField myItnPasswordTextField;
  private JCheckBox myRememberITNPasswordCheckBox;

  public void storeInfo () {
    ErrorReportConfigurable.getInstance().ITN_LOGIN = myItnLoginTextField.getText();
    ErrorReportConfigurable.getInstance().setPlainItnPassword(new String(myItnPasswordTextField.getPassword()));
    ErrorReportConfigurable.getInstance().KEEP_ITN_PASSWORD = myRememberITNPasswordCheckBox.isSelected();
  }

  public void loadInfo () {
    myItnLoginTextField.setText(ErrorReportConfigurable.getInstance().ITN_LOGIN);
    myItnPasswordTextField.setText(ErrorReportConfigurable.getInstance().getPlainItnPassword());
    myRememberITNPasswordCheckBox.setSelected(ErrorReportConfigurable.getInstance().KEEP_ITN_PASSWORD);
  }

  public EAPSendErrorDialog() throws HeadlessException {
    super(false);

    init ();
  }

  protected JPanel myMainPanel;
  protected JTextArea myErrorDescriptionTextArea;
  private Action mySendAction;
  private Action myCancelAction;
  protected JLabel mySendingSettingsLabel;

  private boolean myShouldSend = false;

  private UndoManager undoManager = new UndoManager ();

  public boolean isShouldSend() {
    return myShouldSend;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.diagnostic.AbstractSendErrorDialog";
  }

  protected void init() {
    setTitle(ReportMessages.ERROR_REPORT);
    getContentPane().add(myMainPanel);
    mySendAction = new AbstractAction(DiagnosticBundle.message("diagnostic.error.report.send")) {
      public void actionPerformed(ActionEvent e) {
        myShouldSend = true;
        storeInfo();
        Disposer.dispose(EAPSendErrorDialog.this);
      }
    };
    mySendAction.putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_S));
    mySendAction.putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE.toString());
    myCancelAction = new AbstractAction(CommonBundle.getCancelButtonText()) {
      public void actionPerformed(ActionEvent e) {
        myShouldSend = false;
        Disposer.dispose(EAPSendErrorDialog.this);
      }
    };
    myCancelAction.putValue(Action.MNEMONIC_KEY, new Integer (KeyEvent.VK_C));

    mySendingSettingsLabel.addMouseListener(new MouseAdapter () {
      public void mouseClicked(MouseEvent e) {

        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog ();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });
    mySendingSettingsLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));

    loadInfo();


    myErrorDescriptionTextArea.getDocument().addUndoableEditListener(new UndoableEditListener () {
      public void undoableEditHappened(UndoableEditEvent e) {
        undoManager.addEdit(e.getEdit());
      }
    });
    Keymap keymap = myErrorDescriptionTextArea.getKeymap();
    Shortcut [] undoShortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("$Undo");
    Shortcut [] redoShortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("$Redo");

    //noinspection HardCodedStringLiteral
    Action undoAction = new AbstractAction ("Undo") {
      public void actionPerformed(ActionEvent e) {
        if (undoManager.canUndo())
          undoManager.undo();
      }
    };

    //noinspection HardCodedStringLiteral
    Action redoAction = new AbstractAction ("Redo") {
      public void actionPerformed(ActionEvent e) {
        if (undoManager.canRedo())
          undoManager.redo();
      }
    };

    for (Shortcut undoShortcut : undoShortcuts) {
      if (undoShortcut instanceof KeyboardShortcut) {
        keymap.addActionForKeyStroke(((KeyboardShortcut)undoShortcut).getFirstKeyStroke(), undoAction);
      }
    }

    for (Shortcut redoShortcut : redoShortcuts) {
      if (redoShortcut instanceof KeyboardShortcut) {
        keymap.addActionForKeyStroke(((KeyboardShortcut)redoShortcut).getFirstKeyStroke(), redoAction);
      }
    }

    myErrorDescriptionTextArea.setKeymap(keymap);

    super.init ();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected Action[] createActions() {
    return new Action [] {mySendAction, myCancelAction};
  }

  public String getErrorDescription() {
    return myErrorDescriptionTextArea.getText();
  }

  public void setErrorDescription (String description) {
    myErrorDescriptionTextArea.setText(description);
  }


}
