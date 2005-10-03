/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.messager.Callout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class IdeMessagePanel extends JPanel implements MessagePoolListener {

  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.IdeMessagePanel");

  private IconPane myIdeFatal;

  private IconPane[] myIcons;
  private static final String INTERNAL_ERROR_NOTICE = DiagnosticBundle.message("error.notification.tooltip");

  private long myPreviousExceptionTimeStamp;
  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private final MessagePool myMessagePool;

  public IdeMessagePanel(MessagePool messagePool) {
    super(new BorderLayout());
    myIdeFatal = new IconPane(IconLoader.getIcon("/general/ideFatalError.png"),
                              DiagnosticBundle.message("error.notification.empty.text"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        openFatals();
      }
    });

    myIdeFatal.setVerticalAlignment(JLabel.CENTER);

    myIcons = new IconPane[]{myIdeFatal};
    add(myIdeFatal, BorderLayout.CENTER);

    myMessagePool = messagePool;
    messagePool.addListener(this);

    new Blinker().start();
    updateFatalErrorsIcon();
  }

  private void openFatals() {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    new Thread() {
      public void run() {
        try {
          while(isOtherModalWindowActive()) {
            if (myDialog != null) return;
            sleep(300);
          }

          _openFatals();
        } catch (InterruptedException e) {}
        finally {
          myOpeningInProgress = false;
        }
      }
    }.start();
  }

  private void _openFatals() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myDialog = new IdeErrorsDialog(myMessagePool) {
          protected void doOKAction() {
            super.doOKAction();
            disposeDialog(this);
          }

          public void doCancelAction() {
            super.doCancelAction();
            disposeDialog(this);
          }
        };

        myMessagePool.addListener(myDialog);
        if (!isOtherModalWindowActive()) {
          myDialog.show();
        }
        else {
          myDialog.close(0);
          disposeDialog(myDialog);
        }
      }
    });
  }

  private void disposeDialog(final IdeErrorsDialog listDialog) {
    myMessagePool.removeListener(listDialog);
    updateFatalErrorsIcon();
    myDialog = null;
  }

  public void newEntryAdded() {
    updateFatalErrorsIcon();

    long lastExceptionTimestamp = System.currentTimeMillis();
    if ((lastExceptionTimestamp - myPreviousExceptionTimeStamp > 1000) && myMessagePool.hasUnreadMessages()){
      showErrorCallout();
    }

    myPreviousExceptionTimeStamp = lastExceptionTimestamp;
  }

  private void showErrorCallout() {
    if (PropertiesComponent.getInstance().isTrueValue(IdeErrorsDialog.IMMEDIATE_POPUP_OPTION)) {
      openFatals();
    }
  }

  public void poolCleared() {
    updateFatalErrorsIcon();
  }

  private boolean isOtherModalWindowActive() {
    final Window window = getActiveModalWindow();
    if (window == null) return false;

    if (myDialog != null) {
      return myDialog.getWindow() != window;
    }

    return true;
  }

  private Window getActiveModalWindow() {
    final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Window activeWindow = manager.getActiveWindow();
    if (activeWindow instanceof JDialog) {
      if (((JDialog) activeWindow).isModal()) {
        return activeWindow;
      }
    }

    return null;
  }

  private void updateFatalErrorsIcon() {
    if (myMessagePool.getFatalErrors(true, true).size() == 0) {
      myIdeFatal.deactivate();
    } else {
      myIdeFatal.activate(INTERNAL_ERROR_NOTICE, true);
    }
  }

  private class Blinker extends Thread {
    /** @noinspection BusyWait*/
    public void run() {
      while(true) {
        try {
          sleep(1000);
          setBlinkedIconsVisibilityTo(false);
          sleep(1000);
          setBlinkedIconsVisibilityTo(true);
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
    }

    private void setBlinkedIconsVisibilityTo(boolean aVisible) {
      for (int i = 0; i < myIcons.length; i++) {
        final IdeMessagePanel.IconPane each = myIcons[i];
        each.getIconWrapper().setVisible(aVisible || !each.shouldBlink());
      }
    }
  }

  private class IconPane extends JLabel {

    private IconWrapper myIcon;
    private String myEmptyText;
    private boolean myIsActive;
    private ActionListener myListener;

    public IconPane(Icon aIcon, String aEmptyText, ActionListener aListener) {
      myIcon = new IconWrapper(aIcon);
      myEmptyText = aEmptyText;
      myListener = aListener;
      setIcon(myIcon);

      addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (myIsActive) {
            myListener.actionPerformed(null);
          }
        }
      });

      deactivate();
    }

    public IconWrapper getIconWrapper() {
      return myIcon;
    }

    public void activate(String aDisplayingText, boolean aShouldBlink) {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myIsActive = true;
      myIcon.setIconPainted(true);
      setToolTipText(aDisplayingText);
      repaint();
    }

    public void deactivate() {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      myIsActive = false;
      myIcon.setIconPainted(false);
      setToolTipText(myEmptyText);
      repaint();
    }

    public boolean shouldBlink() {
      return myMessagePool.hasUnreadMessages();
    }
  }

  private class IconWrapper implements Icon {
    private Icon myIcon;
    private boolean myEnabled;
    private boolean myShouldPaint = true;

    public IconWrapper(Icon aIcon) {
      myIcon = aIcon;
    }

    public void setIconPainted(boolean aPainted) {
      myEnabled = aPainted;
    }

    public int getIconHeight() {
      return myIcon.getIconHeight();
    }

    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (myEnabled && myShouldPaint) {
        myIcon.paintIcon(c, g, x, y);
      }
    }

    public void setVisible(final boolean visible) {
      if (myShouldPaint != visible) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            repaint();
          }
        });
      }
      myShouldPaint = visible;
    }
  }
}
