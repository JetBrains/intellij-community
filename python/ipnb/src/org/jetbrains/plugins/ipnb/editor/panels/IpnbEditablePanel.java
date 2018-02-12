package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
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
  protected JTextArea myEditableTextArea;
  public final static String EDITABLE_PANEL = "Editable panel";
  public final static String VIEW_PANEL = "View panel";
  protected OnePixelSplitter mySplitter;
  protected JPanel myViewPrompt;
  private JPanel myEditablePrompt;
  protected JLabel myPromptLabel;
  protected Runnable myOnFinish;

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
    setBorder(BorderFactory.createLineBorder(IpnbEditorUtil.getBackground()));
    addRightClickMenu();
  }

  private void addEditablePanel() {
    myEditableTextArea = createEditablePanel();
    myEditablePrompt = new JPanel(new GridBagLayout());

    myEditablePrompt.setName(EDITABLE_PANEL);
    myEditablePrompt.setBackground(IpnbEditorUtil.getBackground());
    addPromptPanel(myEditablePrompt, null, IpnbEditorUtil.PromptType.None, myEditableTextArea);
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

    JLabel promptComponent = IpnbEditorUtil.createPromptComponent(promptNumber, promptType);
    if (promptType == IpnbEditorUtil.PromptType.In) {
      myPromptLabel = promptComponent;
    }
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
    IdeFocusManager.getGlobalInstance().requestFocus(myEditableTextArea, true);
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

  public void onFinishExecutionAction(Runnable onFinish) {
    myOnFinish = onFinish;
  }

  public void runCell(boolean selectNext) {
    if (mySplitter != null) {
      updateCellSource();
      updateCellView();
      mySplitter.setFirstComponent(myViewPrompt);
      mySplitter.setSecondComponent(null);
      setEditing(false);
      final Container parent = getParent();
      if (parent instanceof IpnbFilePanel) {
        IdeFocusManager.getGlobalInstance().requestFocus(parent, true);
        if (selectNext) {
          ((IpnbFilePanel)parent).selectNext(this, true);
        }
      }
      if (myOnFinish != null) {
        myOnFinish.run();
        myOnFinish = null;
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
          if (parent instanceof IpnbFilePanel) {
            ((IpnbFilePanel)parent).setSelectedCellPanel(IpnbEditablePanel.this);
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
              IdeFocusManager.getGlobalInstance().requestFocus(textArea, true);
            });
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
            IdeFocusManager.getGlobalInstance().requestFocus(parent, true);
          }
        }
      }
    });
    return textArea;
  }

  public boolean contains(int y) {
    return y >= getTop() && y <= getBottom();
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
    ApplicationManager.getApplication().getMessageBus().syncPublisher(IpnbFilePanel.TOPIC).modeChanged(myEditing, editing);
    myEditing = editing;
    setBorder(BorderFactory.createLineBorder(editing ? JBColor.GREEN : JBColor.GRAY));
  }

  public void updateCellView() {
  }

  public int getCaretPosition() {
    return (myEditing && myEditableTextArea != null) ? myEditableTextArea.getCaretPosition() : -1;
  }

  @Override
  protected void addRightClickMenu() {
    myViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbMergeCellAboveAction(), new IpnbMergeCellBelowAction());
          final ListPopup menu = createPopupMenu(group);
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
    myEditableTextArea.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbSplitCellAction());
          final ListPopup menu = createPopupMenu(group);
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
  }

  

  @Nullable
  public String getText(int from, int to) {
    if (myEditing && myEditableTextArea != null) {
      try {
        return myEditableTextArea.getDocument().getText(from, to - from);
      }
      catch (BadLocationException e) {
        LOG.warn(e.getMessage());
      }
    }
    return null;
  }

  public String getText(int from) {
    if (myEditing && myEditableTextArea != null) {
      final Document document = myEditableTextArea.getDocument();
      final int to = document.getLength();
      return getText(from, to);
    }
    return null;
  }

  public void updateCellSource() {
    final String text = myEditableTextArea.getText();
    if (StringUtil.isEmpty(text) && myCell.getSource().isEmpty()) {
      return;
    }
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

  public JTextArea getEditableTextArea() {
    return myEditableTextArea;
  }

  public void dispose() {
    removeAll();
  }
}
