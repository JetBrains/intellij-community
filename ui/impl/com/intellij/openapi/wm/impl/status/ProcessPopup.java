package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ProcessPopup  {

  private VerticalBox myProcessBox = new VerticalBox();

  private InfoAndProgressPanel myProgressPanel;

  static final String BACKGROUND_PROCESSES = "Background Processes";
  private JBPopup myPopup;

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
    final JPanel component = new JPanel(new BorderLayout()) {
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
    component.setFocusable(true);
    component.setBorder(DialogWrapper.ourDefaultBorder);
    component.add(myProcessBox, BorderLayout.NORTH);

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component);
    builder.addListener(new JBPopupListener() {
      public void onClosed(final JBPopup popup) {
        myProgressPanel.hideProcessPopup();
      }
    });
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(BACKGROUND_PROCESSES);
    builder.setDimensionServiceKey("BackgroundProcessPopup2", true);
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

}
