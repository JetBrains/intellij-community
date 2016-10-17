package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellAboveAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellBelowAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbSplitCellAction;
import org.jetbrains.plugins.ipnb.format.cells.IpnbEditableCell;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
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
  private OnePixelSplitter mySplitter;
  private JPanel myViewPrompt;
  private JPanel myEditablePrompt;

  public IpnbEditablePanel(@NotNull K cell) {
    super(cell);
  }

  public IpnbEditablePanel(@NotNull K cell, @NotNull final LayoutManager layoutManager) {
    super(cell, layoutManager);
  }

  protected void initPanel() {
    mySplitter = new OnePixelSplitter(true);
    addViewPanel();
    addEditablePanel();
    mySplitter.setFirstComponent(myViewPrompt);
    mySplitter.setSecondComponent(null);
    setBackground(IpnbEditorUtil.getBackground());
    add(mySplitter);
    addRightClickMenu();
  }

  private void addEditablePanel() {
    myEditablePanel = createEditablePanel();
    myEditablePrompt = new JPanel(new GridBagLayout());

    myEditablePrompt.setName(EDITABLE_PANEL);
    myEditablePrompt.setBackground(IpnbEditorUtil.getBackground());
    addPromptPanel(myEditablePrompt, null, IpnbEditorUtil.PromptType.None, myEditablePanel);
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

    myViewPrompt = new JPanel(new GridBagLayout());
    addPromptPanel(myViewPrompt, null, IpnbEditorUtil.PromptType.None, myViewPanel);
    myViewPrompt.setBackground(IpnbEditorUtil.getBackground());
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
    c.insets = JBUI.insets(2, 2, 2, 5);
    parent.add(promptComponent, c);

    c.gridx = 1;
    c.weightx = 1;
    c.insets = JBUI.insets(2);
    c.anchor = GridBagConstraints.CENTER;
    parent.add(component, c);
  }


  public void switchToEditing() {
    setEditing(true);

    mySplitter.setFirstComponent(myEditablePrompt);
    UIUtil.requestFocus(myEditablePanel);
    mySplitter.setSecondComponent(null);
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

  public void runCell(boolean selectNext) {
    if (mySplitter != null) {
      updateCellSource();
      updateCellView();
      mySplitter.setFirstComponent(myViewPrompt);
      mySplitter.setSecondComponent(null);
      setEditing(false);
      final Container parent = getParent();
      if (parent instanceof IpnbFilePanel) {
        UIUtil.requestFocus((IpnbFilePanel)parent);
        if (selectNext) {
          ((IpnbFilePanel)parent).selectNext(this, true);
        }
      }
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

  public int getCaretPosition() {
    return (myEditing && myEditablePanel != null) ? myEditablePanel.getCaretPosition() : -1;
  }

  public void addRightClickMenu() {
    myViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbMergeCellAboveAction(), new IpnbMergeCellBelowAction());
          final ListPopup menu = createClickMenu(group);
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
    myEditablePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbSplitCellAction());
          final ListPopup menu = createClickMenu(group);
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
  }

  protected ListPopup createClickMenu(@NotNull DefaultActionGroup group) {
    final DataContext context = DataManager.getInstance().getDataContext(this);
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                                                                true);
  }

  @Nullable
  public String getText(int from, int to) {
    if (myEditing && myEditablePanel != null) {
      try {
        return myEditablePanel.getDocument().getText(from, to - from);
      }
      catch (BadLocationException e) {
        LOG.warn(e.getMessage());
      }
    }
    return null;
  }

  public String getText(int from) {
    if (myEditing && myEditablePanel != null) {
      final Document document = myEditablePanel.getDocument();
      final int to = document.getLength();
      return getText(from, to);
    }
    return null;
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
