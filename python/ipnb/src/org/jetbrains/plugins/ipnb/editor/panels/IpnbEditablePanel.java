package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbEditableCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

public abstract class IpnbEditablePanel<T extends JComponent, K extends IpnbEditableCell> extends IpnbPanel<T, K> {
  private static final Logger LOG = Logger.getInstance(IpnbEditablePanel.class);
  private boolean myEditing;
  protected JTextArea myEditablePanel;
  public final static String EDITABLE_PANEL = "Editable panel";
  public final static String VIEW_PANEL = "View panel";
  protected boolean isRunning = false;

  public IpnbEditablePanel(@NotNull K cell) {
    super(cell);
  }

  public IpnbEditablePanel(@NotNull K cell, @NotNull final LayoutManager layoutManager) {
    super(cell, layoutManager);
  }


  protected void initPanel() {
    addViewPanel();
    addEditablePanel();

  }

  private void addEditablePanel() {
    myEditablePanel = createEditablePanel();
    final JPanel panel = new JPanel(new GridBagLayout());

    panel.setName(EDITABLE_PANEL);
    panel.setBackground(IpnbEditorUtil.getBackground());
    addPromptPanel(panel, null, IpnbEditorUtil.PromptType.None, myEditablePanel);

    add(panel, EDITABLE_PANEL);
  }

  private void addViewPanel() {
    myViewPanel = createViewPanel();
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

    final JPanel panel = new JPanel(new GridBagLayout());
    addPromptPanel(panel, null, IpnbEditorUtil.PromptType.None, myViewPanel);
    panel.setBackground(IpnbEditorUtil.getBackground());
    add(panel, VIEW_PANEL);
  }

  public void addPromptPanel(@NotNull final JComponent parent, Integer promptNumber,
                             @NotNull final IpnbEditorUtil.PromptType promptType,
                             @NotNull final JComponent component) {
    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;

    c.weightx = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    Integer number = promptNumber;
    if (isRunning) {
      number = -1;
    }

    final JComponent promptComponent = IpnbEditorUtil.createPromptComponent(number, promptType);
    c.insets = new Insets(2,2,2,5);
    parent.add(promptComponent, c);

    c.gridx = 1;
    c.weightx = 1;
    c.insets = new Insets(2,2,2,2);
    c.anchor = GridBagConstraints.CENTER;
    parent.add(component, c);
  }


  public void switchToEditing() {
    setEditing(true);

    final LayoutManager layout = getLayout();
    if (layout instanceof CardLayout) {
      ((CardLayout)layout).show(this, EDITABLE_PANEL);
      UIUtil.requestFocus(myEditablePanel);
    }
  }

  public boolean isModified() {
    final Component[] components = getComponents();
    for (Component component : components) {
      final String name = component.getName();
      if (component.isVisible() && EDITABLE_PANEL.equals(name)) return true;
    }
    return false;
  }

  protected String getRawCellText() { return ""; }

  public void runCell() {
    final LayoutManager layout = getLayout();
    if (layout instanceof CardLayout) {
      updateCellSource();
      updateCellView();
      ((CardLayout)layout).show(this, VIEW_PANEL);
      setEditing(false);
    }
  }

  private JTextArea createEditablePanel() {
    final JTextArea textArea = new JTextArea(getRawCellText());
    textArea.setLineWrap(true);
    textArea.setEditable(true);
    textArea.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    textArea.setBackground(IpnbEditorUtil.getEditablePanelBackground());
    textArea.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          setEditing(true);
          final Container parent = getParent();
          parent.repaint();
          if (parent instanceof IpnbFilePanel) {
            ((IpnbFilePanel)parent).setSelectedCell(IpnbEditablePanel.this);
            textArea.requestFocus();
          }
        }
      }
    });
    textArea.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          setEditing(false);
          final Container parent = getParent();
          if (parent instanceof IpnbFilePanel) {
            parent.repaint();
            UIUtil.requestFocus((IpnbFilePanel)parent);
          }
        }
      }
    });
    return textArea;
  }

  public boolean contains(int y) {
    return y>= getTop() && y<=getBottom();
  }

  public int getTop() {
    return getY();
  }

  public int getBottom() {
    return getTop() + getHeight();
  }

  public boolean isEditing() {
    return myEditing;
  }

  public void setEditing(boolean editing) {
    myEditing = editing;
  }

  public void updateCellView() { // TODO: make abstract
  }

  public void updateCellSource() {
    final String text = myEditablePanel.getText();
    myCell.setSource(Arrays.asList(StringUtil.splitByLinesKeepSeparators(text != null ? text : "")));
  }

  @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
  @Override
  protected Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  public K getCell() {
    return myCell;
  }

}
