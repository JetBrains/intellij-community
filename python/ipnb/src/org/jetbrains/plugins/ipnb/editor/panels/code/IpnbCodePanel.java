package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellAboveAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellBelowAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbSplitCellAction;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IpnbCodePanel extends IpnbEditablePanel<JComponent, IpnbCodeCell> {
  private final Project myProject;
  @NotNull private final IpnbFileEditor myParent;
  private final static String COLLAPSED_METADATA = "collapsed";
  private IpnbCodeSourcePanel myCodeSourcePanel;
  private final List<IpnbPanel> myOutputPanels = Lists.newArrayList();
  private boolean mySelectNext;

  public IpnbCodePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new BorderLayout());
    myProject = project;
    myParent = parent;

    myViewPanel = createViewPanel();
    add(myViewPanel);
    addRightClickMenu();
  }

  @NotNull
  public IpnbFileEditor getFileEditor() {
    return myParent;
  }

  public Editor getEditor() {
    return myCodeSourcePanel.getEditor();
  }

  public void addPromptPanel(@NotNull final JComponent parent, Integer promptNumber,
                             @NotNull final IpnbEditorUtil.PromptType promptType,
                             @NotNull final JComponent component) {
    super.addPromptPanel(parent, promptNumber, promptType, component);
    if (component instanceof IpnbPanel) {
      myOutputPanels.add((IpnbPanel)component);
    }
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    panel.setBackground(IpnbEditorUtil.getBackground());
    panel.add(createCodeComponent());
    panel.add(createHideableOutputPanel());

    return panel;
  }

  @Override
  public void addRightClickMenu() {
    myCodeSourcePanel.addMouseListener(new EditorMouseAdapter() {
      @Override
      public void mousePressed(EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (SwingUtilities.isRightMouseButton(mouseEvent) && mouseEvent.getClickCount() == 1) {
          final ListPopup menu = createClickMenu(new DefaultActionGroup(new IpnbMergeCellAboveAction(), new IpnbMergeCellBelowAction(),
                                                                        new IpnbSplitCellAction()));
          menu.show(RelativePoint.fromScreen(e.getMouseEvent().getLocationOnScreen()));
        }
      }
    });
  }

  @NotNull
  private JPanel createCodeComponent() {
    myCodeSourcePanel = new IpnbCodeSourcePanel(myProject, this, myCell);
    
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());
    addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel);
    
    final JPanel topComponent = new JPanel(new BorderLayout());
    topComponent.add(panel, BorderLayout.PAGE_START);
    return topComponent;
  }

  public JPanel createHideableOutputPanel() {
    final OnePixelSplitter splitter = new OnePixelSplitter(true);
    final JPanel toggleBar = createToggleBar(splitter);
    final JPanel outputComponent = createOutputPanel(createHideOutputListener(splitter, toggleBar));
    
    final Map<String, Object> metadata = myCell.getMetadata();
    if (metadata.containsKey(COLLAPSED_METADATA)) {
      final boolean isCollapsed = (Boolean)metadata.get(COLLAPSED_METADATA);
      if (isCollapsed && !myCell.getCellOutputs().isEmpty()) {
        splitter.setFirstComponent(toggleBar);
        return splitter;
      }
    }
    splitter.setSecondComponent(outputComponent);

    return splitter;
  }

  @NotNull
  private JPanel createOutputPanel(MouseAdapter hideOutputListener) {
    final JPanel outputPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, true, false));
    outputPanel.setBackground(IpnbEditorUtil.getBackground());
    
    for (IpnbOutputCell outputCell : myCell.getCellOutputs()) {
      addOutputPanel(outputPanel, outputCell, hideOutputListener, true);
    }

    outputPanel.addMouseListener(hideOutputListener);
    return outputPanel;
  }

  @NotNull
  private MouseAdapter createHideOutputListener(final OnePixelSplitter splitter, final JPanel toggleBar) {
    return new MouseAdapter() {
      private static final String TOGGLE_OUTPUT_TEXT = " Toggle output                                                (Double-Click)";

      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          hideOutputPanel();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final JPopupMenu menu = new JPopupMenu("");
          
          final JMenuItem item = new JMenuItem(TOGGLE_OUTPUT_TEXT);
          item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              hideOutputPanel();
            }
          });
          
          menu.add(item);
          menu.show(e.getComponent(), e.getX(), e.getY());
        }
      }

      private void hideOutputPanel() {
        setOutputStateInCell(true);
        splitter.setFirstComponent(toggleBar);
        splitter.setSecondComponent(null);
      }
    };
  }

  @NotNull
  private MouseAdapter createShowOutputListener(final OnePixelSplitter splitter, final JPanel secondPanel, JLabel label) {
    return new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          showOutputPanel();
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        updateBackground(UIUtil.getListSelectionBackground());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        updateBackground(IpnbEditorUtil.getBackground());
      }

      private void updateBackground(Color background) {
        secondPanel.setBackground(background);
        label.setBackground(background);
      }

      private void showOutputPanel() {
        setOutputStateInCell(false);
        updateBackground(IpnbEditorUtil.getBackground());
        splitter.setFirstComponent(null);
        final JPanel outputPanel = createOutputPanel(createHideOutputListener(splitter, IpnbCodePanel.this.createToggleBar(splitter)));
        splitter.setSecondComponent(outputPanel);
      }
    };
  }

  private void setOutputStateInCell(boolean isCollapsed) {
    final Map<String, Object> metadata = myCell.getMetadata();
    metadata.put("collapsed", isCollapsed);
  }

  private JPanel createToggleBar(OnePixelSplitter splitter) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(AllIcons.Actions.Down);
    panel.setBackground(IpnbEditorUtil.getBackground());
    label.setBackground(IpnbEditorUtil.getBackground());
    panel.add(label, BorderLayout.CENTER);

    panel.addMouseListener(createShowOutputListener(splitter, panel, label));

    return panel;
  }

  private void addOutputPanel(@NotNull final JComponent mainPanel,
                              @NotNull final IpnbOutputCell outputCell, MouseAdapter hideOutputListener, boolean addPrompt) {
    final IpnbEditorUtil.PromptType promptType = addPrompt ? IpnbEditorUtil.PromptType.Out : IpnbEditorUtil.PromptType.None;
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());
    if (outputCell instanceof IpnbImageOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbImagePanel((IpnbImageOutputCell)outputCell, hideOutputListener));
    }
    else if (outputCell instanceof IpnbHtmlOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbHtmlPanel((IpnbHtmlOutputCell)outputCell, myParent.getIpnbFilePanel(), hideOutputListener));
    }
    else if (outputCell instanceof IpnbLatexOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbLatexPanel((IpnbLatexOutputCell)outputCell, myParent.getIpnbFilePanel(), hideOutputListener));
    }
    else if (outputCell instanceof IpnbErrorOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbErrorPanel((IpnbErrorOutputCell)outputCell, hideOutputListener));
    }
    else if (outputCell instanceof IpnbStreamOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.None,
                     new IpnbStreamPanel((IpnbStreamOutputCell)outputCell, hideOutputListener));
    }
    else if (outputCell.getSourceAsString() != null) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbCodeOutputPanel<>(outputCell, myParent.getIpnbFilePanel(), hideOutputListener));
    }
    mainPanel.add(panel);
  }

  @Override
  public void switchToEditing() {
    setEditing(true);
    final Container parent = getParent();
    if (parent != null) {
      parent.repaint();
    }
    UIUtil.requestFocus(myCodeSourcePanel.getEditor().getContentComponent());
  }

  @Override
  public void runCell(boolean selectNext) {
    mySelectNext = selectNext;
    updateCellSource();
    isRunning = true;
    updatePanel(null, null);
    final IpnbConnectionManager connectionManager = IpnbConnectionManager.getInstance(myProject);
    connectionManager.executeCell(this);
    setEditing(false);
  }

  @Override
  public boolean isModified() {
    return true;
  }

  @Override
  public void updateCellSource() {
    final Document document = myCodeSourcePanel.getEditor().getDocument();
    final String text = document.getText();
    myCell.setSource(Arrays.asList(StringUtil.splitByLinesKeepSeparators(text)));
  }

  public void updatePanel(@Nullable final String replacementContent, @Nullable final List<IpnbOutputCell> outputContent) {
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      if (replacementContent != null) {
        myCell.setSource(Arrays.asList(StringUtil.splitByLinesKeepSeparators(replacementContent)));
        application.runWriteAction(() -> myCodeSourcePanel.getEditor().getDocument().setText(replacementContent));
      }
      myCell.removeCellOutputs();
      myViewPanel.removeAll();

      isRunning = false;
      if (outputContent != null) {
        for (IpnbOutputCell output : outputContent) {
          myCell.addCellOutput(output);
        }
      }

      final JComponent panel = createViewPanel();
      myViewPanel.add(panel);

      final IpnbFilePanel filePanel = myParent.getIpnbFilePanel();
      setEditing(false);
      filePanel.revalidateAndRepaint();
      if (mySelectNext && (replacementContent != null || outputContent != null)) {
        filePanel.selectNext(this, true);
      }
    }, ModalityState.stateForComponent(this));
  }

  @Override
  public void updateCellView() {
    myViewPanel.removeAll();
    final JComponent panel = createViewPanel();
    myViewPanel.add(panel);

    final IpnbFilePanel filePanel = myParent.getIpnbFilePanel();
    filePanel.revalidate();
    filePanel.repaint();
  }

  @Override
  public int getCaretPosition() {
    return myCodeSourcePanel.getEditor().getCaretModel().getOffset();
  }

  @Nullable
  @Override
  public String getText(int from, int to) {
    return myCodeSourcePanel.getEditor().getDocument().getText(new TextRange(from, to));
  }

  @Override
  public String getText(int from) {
    return getText(from, myCodeSourcePanel.getEditor().getDocument().getTextLength());
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
  @Override
  protected Object clone() {
    return new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)myCell.clone());
  }
}
