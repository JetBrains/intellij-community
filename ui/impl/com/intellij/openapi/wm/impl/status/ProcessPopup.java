package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.HashSet;

public class ProcessPopup  {

  private VerticalBox myProcessBox = new VerticalBox();

  private InfoAndProgressPanel myProgressPanel;

  private JBPopup myPopup;

  private JPanel myActiveFocusedContent;
  private JComponent myActiveContentComponent;

  private JLabel myInactiveContentComponent;

  private Wrapper myRootContent = new Wrapper();

  private final Set<InlineProgressIndicator> myIndicators = new HashSet<InlineProgressIndicator>();

  public ProcessPopup(final InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    buildActiveContent();
    myInactiveContentComponent = new JLabel(IdeBundle.message("progress.window.empty.text"), null, JLabel.CENTER) {
      public Dimension getPreferredSize() {
        return getEmptyPreferredSize();
      }
    };
    myInactiveContentComponent.setFocusable(true);

    switchToPassive();
  }

  public void addIndicator(InlineProgressIndicator indicator) {
    myIndicators.add(indicator);

    myProcessBox.add(indicator.getComponent());
    myProcessBox.add(Box.createVerticalStrut(4));

    swithToActive();

    revalidateAll();
  }

  public void removeIndicator(InlineProgressIndicator indicator) {
    if (indicator.getComponent().getParent() != myProcessBox) return;

    removeExtraSeparator(indicator);
    myProcessBox.remove(indicator.getComponent());

    myIndicators.remove(indicator);
    switchToPassive();

    revalidateAll();
  }

  private void swithToActive() {
    if (myActiveContentComponent.getParent() == null && myIndicators.size() > 0) {
      myRootContent.removeAll();
      myRootContent.setContent(myActiveContentComponent);
    }
  }

  private void switchToPassive() {
    if (myInactiveContentComponent.getParent() == null && myIndicators.size() == 0) {
      myRootContent.removeAll();
      myRootContent.setContent(myInactiveContentComponent);
    }
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
    JComponent toFocus = myRootContent.getTargetComponent() == myActiveContentComponent ? myActiveFocusedContent : myInactiveContentComponent;

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myRootContent, toFocus);
    builder.addListener(new JBPopupListener() {
      public void onClosed(final JBPopup popup) {
        myProgressPanel.hideProcessPopup();
      }
    });
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setDimensionServiceKey(null, "ProcessPopupWindow", true);
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(true);

    builder.setCancelButton(new MinimizeButton("Hide"));

    myPopup = builder.createPopup();
    myPopup.showInCenterOf(myProgressPanel.getRootPane());
  }

  private void buildActiveContent() {
    myActiveFocusedContent = new ActiveContent();

    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(myProcessBox, BorderLayout.NORTH);

    myActiveFocusedContent.add(wrapper, BorderLayout.CENTER);

    final JScrollPane scrolls = new JScrollPane(myActiveFocusedContent) {
      public Dimension getPreferredSize() {
        if (myProcessBox.getComponentCount() > 0) {
          return super.getPreferredSize();
        } else {
          return getEmptyPreferredSize();
        }
      }
    };
    scrolls.getViewport().setBackground(myActiveFocusedContent.getBackground());
    scrolls.setBorder(null);
    myActiveContentComponent = scrolls;
  }

  private Dimension getEmptyPreferredSize() {
    final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    size.width *= 0.3d;
    size.height *= 0.3d;
    return size;
  }

  public void hide() {
    if (myPopup != null) {
      final JBPopup popup = myPopup;
      myPopup = null;
      popup.cancel();
    }
  }

  public boolean isShowing() {
    return myPopup != null;
  }


  private class ActiveContent extends JPanel implements Scrollable {

    private JLabel myLabel = new JLabel("XXX");

    public ActiveContent() {
      super(new BorderLayout());
      setBorder(DialogWrapper.ourDefaultBorder);
      setFocusable(true);
    }


    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private void revalidateAll() {
    myRootContent.revalidate();
    myRootContent.repaint();
  }

}
