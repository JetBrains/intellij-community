package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.ui.KeyStrokeAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class IpnbMarkdownPanel extends IpnbEditablePanel<JComponent, IpnbMarkdownCell> {

  private final IpnbFilePanel myParent;

  public IpnbMarkdownPanel(@NotNull final IpnbMarkdownCell cell, @NotNull final IpnbFilePanel parent) {
    super(cell);
    myParent = parent;
    initPanel();
    addKeyListener(new KeyStrokeAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        myParent.processKeyPressed(event);
      }
    });
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  @Override
  protected JComponent createViewPanel() {
    int width = myParent.getWidth();
    return IpnbUtils.createLatexPane(myCell.getSourceAsString(), myParent.getProject(), width);
  }

  @Override
  public void updateCellView() {
    int width = myParent.getWidth();
    myViewPanel = IpnbUtils.createLatexPane(myCell.getSourceAsString(), myParent.getProject(), width);
    myViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final Container parent = getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(myViewPanel, e, parent);
        parent.dispatchEvent(parentEvent);
        if (e.getClickCount() == 2) {
          switchToEditing();
        }
      }
    });
    myViewPanel.setName(VIEW_PANEL);

    myViewPrompt = new JPanel(new GridBagLayout());
    addPromptPanel(myViewPrompt, null, IpnbEditorUtil.PromptType.None, myViewPanel);
    myViewPrompt.setBackground(IpnbEditorUtil.getBackground());

    mySplitter.setFirstComponent(myViewPrompt);
    mySplitter.setSecondComponent(null);
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbMarkdownPanel((IpnbMarkdownCell)myCell.clone(), myParent);
  }
}
