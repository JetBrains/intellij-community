package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ArrayUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ProcessPopup  {

  private VerticalBox myProcessBox = new VerticalBox();

  private InfoAndProgressPanel myProgressPanel;

  private JBPopup myPopup;

  private JComponent myContent;

  public ProcessPopup(final InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;
  }

  public void addIndicator(InlineProgressIndicator indicator) {
    myProcessBox.add(indicator.getComponent());
    myProcessBox.add(new SeparatorComponent());
    revalidateAll();
  }

  public void removeIndicator(InlineProgressIndicator indicator) {
    if (indicator.getComponent().getParent() != myProcessBox) return;

    removeExtraSeparator(indicator);

    myProcessBox.remove(indicator.getComponent());
    revalidateAll();
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
    JPanel content = new Content();

    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(myProcessBox, BorderLayout.NORTH);

    content.add(wrapper, BorderLayout.CENTER);

    final JScrollPane scrolls = new JScrollPane(content) {
      public Dimension getPreferredSize() {
        if (myProcessBox.getComponentCount() > 0) {
          return super.getPreferredSize();
        } else {
          final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
          size.width *= 0.3d;
          size.height *= 0.3d;
          return size;
        }
      }
    };
    scrolls.getViewport().setBackground(content.getBackground());
    scrolls.setBorder(null);
    myContent = scrolls;

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(scrolls, content);
    builder.addListener(new JBPopupListener() {
      public void onClosed(final JBPopup popup) {
        myProgressPanel.hideProcessPopup();
      }
    });
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setDimensionServiceKey("ProcessPopupWindow", true);
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(true);

    final IconButton hideButton = new IconButton("Hide", IconLoader.getIcon("/general/hideToolWindow.png"),
                                                 IconLoader.getIcon("/general/hideToolWindow.png"),
                                                 IconLoader.getIcon("/general/hideToolWindowInactive.png"));

    builder.setCancelButton(hideButton);

    myPopup = builder.createPopup();
    myPopup.showInCenterOf(myProgressPanel.getRootPane());
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


  private class Content extends JPanel implements Scrollable {

    private JLabel myLabel = new JLabel("XXX");

    public Content() {
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
    if (myContent != null) {
      myContent.revalidate();
      myContent.repaint();
    }
  }

}
