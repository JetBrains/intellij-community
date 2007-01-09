package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;

public class ProcessPopup  {

  private VerticalBox myProcessBox = new VerticalBox();

  private DialogWrapper myDialog;

  private InfoAndProgressPanel myProgressPanel;

  public ProcessPopup(final InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;
  }

  public void addIndicator(InlineProgressIndicator indicator) {
    myProcessBox.add(indicator.getComponent());
    myProcessBox.add(new SeparatorComponent());
    myProcessBox.revalidate();
    myProcessBox.repaint();
  }

  public void removeIndicator(InlineProgressIndicator indicator) {
    if (indicator.getComponent().getParent() != myProcessBox) return;

    removeExtraSeparator(indicator);

    myProcessBox.remove(indicator.getComponent());
    myProcessBox.revalidate();
    myProcessBox.repaint();
  }

  private void removeExtraSeparator(final InlineProgressIndicator indicator) {
    final Component[] all = myProcessBox.getComponents();
    final int index = ArrayUtil.indexOf(all, indicator.getComponent());
    if (index == -1) return;


    if (index == 0 && all.length > 1) {
      myProcessBox.remove(1);
    } else if (all.length > 2 && index < all.length - 1) {
      myProcessBox.remove(index + 1);
    }

    myProcessBox.remove(indicator.getComponent());
  }

  public void show() {
    myDialog = new MyDialogWrapper(myProgressPanel.myStatusBar);

    myDialog.setTitle("Background Processes");
    myDialog.pack();
    myDialog.show();
  }

  public void hide() {
    if (myDialog != null) {
      myDialog.close(0);
    }
    myDialog = null;
  }

  public boolean isShowing() {
    return myDialog != null;
  }

  private class MyDialogWrapper extends DialogWrapper {
    public MyDialogWrapper(final Component parent) {
      super(parent, false);
      setModal(false);
      setCrossClosesWindow(true);
      init();
    }
    protected JComponent createSouthPanel() {
      return new JLabel();
    }

    public void doCancelAction() {
      myProgressPanel.hideProcessPopup();
    }

    protected String getDimensionServiceKey() {
      return "ProgressWindow";
    }

    protected JComponent createCenterPanel() {
      final JPanel component = new JPanel(new BorderLayout());
      component.add(myProcessBox, BorderLayout.NORTH);
      return component;
    }
  }
}
