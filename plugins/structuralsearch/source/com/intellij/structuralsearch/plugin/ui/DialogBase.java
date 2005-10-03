package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.*;

import org.jetbrains.annotations.NonNls;

/**
 * Base dialog class
 */
public abstract class DialogBase extends JDialog {
  private JButton ok;
  private JButton cancel;

  private Action okAction;
  private Action cancelAction;
  private static Rectangle virtualBounds;

  class OkAction extends AbstractAction {
    OkAction() {
      putValue(NAME, CommonBundle.getOkButtonText());
    }
    public void actionPerformed(ActionEvent e) {
      doOKAction();
    }
  }

  class CancelAction extends AbstractAction {
    CancelAction() {
      putValue(NAME,CommonBundle.getCancelButtonText());
    }

    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  protected DialogBase() {
    this(null);
  }

  protected DialogBase(Frame frame) {
    this(frame,true);
  }

  protected DialogBase(Frame frame,boolean modal) {
    super(frame,modal);

    new MnemonicHelper().register(getContentPane());

    okAction = new OkAction();
    cancelAction = new CancelAction();

    ok = createJButtonForAction(okAction);
    cancel = createJButtonForAction(cancelAction);

    if (virtualBounds == null) {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] gs = ge.getScreenDevices();
      virtualBounds = new Rectangle();

      for (int j = 0; j < gs.length; j++) {
        GraphicsDevice gd = gs[j];
        GraphicsConfiguration[] gc = gd.getConfigurations();

        for (int i=0; i < gc.length; i++) {
          virtualBounds = virtualBounds.union(gc[i].getBounds());
        }
      }
    }

    @NonNls String cancelCommandName = "close";
    KeyStroke escKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0);
    ok.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escKeyStroke, cancelCommandName);
    ok.getActionMap().put(cancelCommandName, cancelAction);

    @NonNls String startCommandName = "start";
    KeyStroke enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,InputEvent.CTRL_MASK);
    ok.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(enterKeyStroke, startCommandName);
    ok.getActionMap().put(startCommandName, okAction);
  }

  protected JButton getCancelButton() {
    return cancel;
  }

  protected JButton getOkButton() {
    return ok;
  }

  protected abstract JComponent createCenterPanel();

  protected JComponent createSouthPanel() {
    JPanel p = new JPanel( null );
    p.setLayout( new BoxLayout(p,BoxLayout.X_AXIS) );
    p.add(Box.createHorizontalGlue());
    p.add(getOkButton());
    p.add(getCancelButton());
    return p;
  }

  public void init() {
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(BorderLayout.CENTER,createCenterPanel());
    getContentPane().add(BorderLayout.SOUTH,createSouthPanel());
    pack();

    Dimension dim = getPreferredSize();
    setLocation(
      (int)(virtualBounds.getWidth()/2 - dim.getWidth()/2),
      (int)(virtualBounds.getHeight()/2 - dim.getHeight()/2)
    );
  }

  public void show() {
    pack();
    super.show();
  }

  protected void doCancelAction() {
    setVisible(false);
  }

  protected void doOKAction() {
    setVisible(false);
  }

  protected void setOKActionEnabled(boolean b) {
    okAction.setEnabled(b);
  }

  protected void setOKButtonText(String text) {
    okAction.putValue(Action.NAME,text);
  }

  protected void setCancelButtonText(String text) {
    cancelAction.putValue(Action.NAME,text);
  }

  protected JButton createJButtonForAction(Action _action) {
    JButton jb = new JButton( (String)_action.getValue(Action.NAME) );
    jb.setAction(_action);

    return jb;
  }
}
