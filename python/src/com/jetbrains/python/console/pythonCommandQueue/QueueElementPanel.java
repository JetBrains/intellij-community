// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.pythonCommandQueue;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.actions.ContentChooser;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Panel for one command (CommandQueue)
 */
public final class QueueElementPanel {
  private final QueueElementButton myCancelButton = createCancelButton();
  private final JBLabel myText = new JBLabel();
  private final Item myItem;
  private final JPanel myRootPanel = new JPanel();
  private final ConsoleCommunication.ConsoleCodeFragment myCodeFragment;
  private final JPanel buttonPanel;
  private static final int TEXT_MAX_LENGTH = 25;
  private static final int LABEL_FONT_SIZE = 13;
  private static final int ROOT_PANEL_MINIMUM_SIZE_WIDTH = 200;
  private static final int ROOT_PANEL_MINIMUM_SIZE_HEIGHT = 20;
  private static final int ROOT_PANEL_PREFERRED_SIZE_WIDTH = -1;
  private static final int ROOT_PANEL_PREFERRED_SIZE_HEIGHT = 20;

  private volatile boolean isCanceled;
  private final @NlsContexts.Tooltip String myCancelTooltipText = CoreBundle.message("button.cancel");

  public QueueElementPanel(@NotNull ConsoleCommunication.ConsoleCodeFragment codeFragment, @Nullable Icon icon) {
    myCodeFragment = codeFragment;
    myText.setFont(JBUI.Fonts.label(LABEL_FONT_SIZE));
    myItem = new Item(codeFragment.getText().trim());
    myText.setText(myItem.getShortText(TEXT_MAX_LENGTH));
    if (icon != null) {
      myText.setIcon(icon);
    }
    buttonPanel = new JPanel();
    buttonPanel.add(createButtonPanel(myCancelButton.button));
    buttonPanel.setOpaque(true);
    buttonPanel.setBackground(JBColor.background());
    buttonPanel.setForeground(JBColor.foreground());

    myRootPanel.setLayout(new BorderLayout());
    myRootPanel.setMinimumSize(new Dimension(ROOT_PANEL_MINIMUM_SIZE_WIDTH, ROOT_PANEL_MINIMUM_SIZE_HEIGHT));
    myRootPanel.setPreferredSize(new Dimension(ROOT_PANEL_PREFERRED_SIZE_WIDTH, ROOT_PANEL_PREFERRED_SIZE_HEIGHT));
    myRootPanel.add(myText, BorderLayout.WEST);
    myRootPanel.add(buttonPanel, BorderLayout.EAST);
    myRootPanel.setBackground(JBColor.background());
    myRootPanel.setFocusable(true);
    myRootPanel.requestFocusInWindow();
    myRootPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        getCommandPanelParent().commandSelected(QueueElementPanel.this);
      }
    });
  }

  @NotNull
  private static JPanel createButtonPanel(@NotNull JComponent component) {
    JPanel iconsPanel = new NonOpaquePanel(new GridBagLayout());
    GridBag gb = new GridBag().setDefaultFill(GridBagConstraints.BOTH);
    iconsPanel.add(component, gb.next());
    return iconsPanel;
  }

  @NotNull
  private QueueElementButton createCancelButton() {
    InplaceButton cancelButton = new InplaceButton(
      new IconButton(myCancelTooltipText,
                     AllIcons.Process.StopHovered,
                     AllIcons.Process.StopHovered),
      __ -> cancelRequest());

    cancelButton.setVisible(true);
    cancelButton.setFillBg(true);

    return new QueueElementButton(cancelButton, () -> cancelButton.setPainting(!isCanceled));
  }

  @Nullable
  public String getText() {
    return myItem.getLongText();
  }

  private void cancelRequest() {
    if (myCancelButton.button.isActive()) {
      isCanceled = true;
      getCommandPanelParent().removeCommandByButton(myCodeFragment);
    }
  }

  private PythonCommandQueuePanel getCommandPanelParent() {
    Container parent = myRootPanel;
    while (!(parent instanceof PythonCommandQueuePanel)) {
      parent = parent.getParent();
    }
    return (PythonCommandQueuePanel)parent;
  }

  public void setIcon(@NotNull Icon icon) {
    myText.setIcon(icon);
  }

  public void unsetCancelButton() {
    buttonPanel.setEnabled(false);
    buttonPanel.setVisible(false);
    buttonPanel.setFocusable(false);
  }

  @NotNull
  public JComponent getQueuePanel() {
    return myRootPanel;
  }

  static class QueueElementButton {
    @NotNull final InplaceButton button;
    @NotNull final Runnable updateAction;

    QueueElementButton(@NotNull InplaceButton button, @NotNull Runnable updateAction) {
      this.button = button;
      this.updateAction = updateAction;
    }
  }

  public void setTextColor() {
    myText.setForeground(UIUtil.getListSelectionForeground(false));
  }

  public void setButtonColor() {
    buttonPanel.setBackground(JBColor.lazy(UIUtil::getListBackground));
  }

  public void selectPanel() {
    myRootPanel.setBackground(UIUtil.getListSelectionBackground(true));
    myText.setForeground(UIUtil.getListSelectionForeground(true));
  }

  private static final class Item extends ContentChooser.Item {
    Item(String longText) {
      super(0, longText);
    }

    public String getLongText() {
      return super.longText;
    }
  }
}