package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbHideOutputAction;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Map;

public class IpnbCodePanel extends IpnbEditablePanel<JComponent, IpnbCodeCell> {
  private final Project myProject;
  @NotNull private final IpnbFileEditor myParent;
  private final static String COLLAPSED_METADATA = "collapsed";
  private IpnbCodeSourcePanel myCodeSourcePanel;
  private HideableOutputPanel myHideableOutputPanel;
  private boolean mySelectNext;

  private JComponent myLastAddedPanel;

  public IpnbCodePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new BorderLayout());
    myProject = project;
    myParent = parent;

    myViewPanel = createViewPanel();
    add(myViewPanel);
    addRightClickMenu();
    addKeyListener(new KeyStrokeAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        myParent.getIpnbFilePanel().processKeyPressed(event);
      }
    });
    setBorder(BorderFactory.createLineBorder(IpnbEditorUtil.getBackground()));
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
    myLastAddedPanel = component;
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    panel.setBackground(IpnbEditorUtil.getBackground());
    panel.add(createCodeComponent());
    myHideableOutputPanel = new HideableOutputPanel();
    panel.add(myHideableOutputPanel);

    return panel;
  }

  @Override
  protected void addRightClickMenu() {
    myHideableOutputPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final ListPopup menu = createPopupMenu(new DefaultActionGroup(new IpnbHideOutputAction(IpnbCodePanel.this)));
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
  }

  public void dispose() {
    removeAll();
    myLastAddedPanel = null;
    myCodeSourcePanel.dispose();
    myCodeSourcePanel = null;
    myHideableOutputPanel.removeAll();
    myHideableOutputPanel = null;
    myViewPanel.removeAll();
    myViewPanel = null;
  }

  class HideableOutputPanel extends OnePixelSplitter {
    final JPanel myToggleBar;
    final JPanel myOutputComponent;

    public HideableOutputPanel() {
      super(true);
      myToggleBar = createToggleBar(this);
      myOutputComponent = createOutputPanel();

      final Map<String, Object> metadata = myCell.getMetadata();
      if (metadata.containsKey(COLLAPSED_METADATA)) {
        final boolean isCollapsed = (Boolean)metadata.get(COLLAPSED_METADATA);
        if (isCollapsed && !myCell.getCellOutputs().isEmpty()) {
          setFirstComponent(myToggleBar);
          return;
        }
      }
      setSecondComponent(myOutputComponent);
    }

    public void hideOutputPanel() {
      setOutputStateInCell(true);
      setFirstComponent(myToggleBar);
      setSecondComponent(null);
    }
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

  @NotNull
  private JPanel createOutputPanel() {
    final JPanel outputPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, true, false));
    outputPanel.setBackground(IpnbEditorUtil.getBackground());

    for (IpnbOutputCell outputCell : myCell.getCellOutputs()) {
      addOutputPanel(outputPanel, outputCell, outputCell instanceof IpnbOutOutputCell);
    }

    return outputPanel;
  }

  public void hideOutputPanel() {
    myHideableOutputPanel.hideOutputPanel();
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
        final JPanel outputPanel = createOutputPanel();
        splitter.setSecondComponent(outputPanel);
      }
    };
  }

  private void setOutputStateInCell(boolean isCollapsed) {
    final Map<String, Object> metadata = myCell.getMetadata();
    if (!metadata.containsKey(COLLAPSED_METADATA) && !isCollapsed) {
      return;
    }

    metadata.put(COLLAPSED_METADATA, isCollapsed);

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
                              @NotNull final IpnbOutputCell outputCell, boolean addPrompt) {
    final IpnbEditorUtil.PromptType promptType = addPrompt ? IpnbEditorUtil.PromptType.Out : IpnbEditorUtil.PromptType.None;
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());
    if (outputCell instanceof IpnbImageOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbImagePanel((IpnbImageOutputCell)outputCell, this));
    }
    else if (outputCell instanceof IpnbHtmlOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbHtmlPanel((IpnbHtmlOutputCell)outputCell, myParent.getIpnbFilePanel(), this));
    }
    else if (outputCell instanceof IpnbLatexOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbLatexPanel((IpnbLatexOutputCell)outputCell, myParent.getIpnbFilePanel(), this));
    }
    else if (outputCell instanceof IpnbErrorOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbErrorPanel((IpnbErrorOutputCell)outputCell, this));
    }
    else if (outputCell instanceof IpnbStreamOutputCell) {
      if (myLastAddedPanel instanceof IpnbStreamPanel) {
        ((IpnbStreamPanel)myLastAddedPanel).addOutput(outputCell);
        return;
      }
      else {
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.None,
                                          new IpnbStreamPanel((IpnbStreamOutputCell)outputCell, this));
      }
    }
    else if (outputCell.getSourceAsString() != null) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbCodeOutputPanel<>(outputCell, myParent.getIpnbFilePanel(), this));
    }
    mainPanel.add(panel);
  }

  @Override
  public void switchToEditing() {
    setEditing(true);
    IdeFocusManager.findInstance().requestFocus(myCodeSourcePanel.getEditor().getContentComponent(), true);
  }

  @Override
  public void runCell(boolean selectNext) {
    mySelectNext = selectNext;
    updateCellSource();
    updatePrompt();
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

  public void updatePrompt() {
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      myCell.setPromptNumber(-1);
      final String promptText = IpnbEditorUtil.prompt(myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In);
      myPromptLabel.setText(promptText);
    }, ModalityState.stateForComponent(this));
  }

  public void finishExecution() {
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      final String promptText = IpnbEditorUtil.prompt(myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In);
      myPromptLabel.setText(promptText);
      final IpnbFilePanel filePanel = myParent.getIpnbFilePanel();
      setEditing(false);
      IdeFocusManager.findInstance().requestFocus(filePanel, true);
      if (mySelectNext) {
        filePanel.selectNext(this, true);
      }
      if (myOnFinish != null) {
        myOnFinish.run();
        myOnFinish = null;
      }
    }, ModalityState.stateForComponent(this));
  }

  public void updatePanel(@Nullable final String replacementContent, @Nullable final IpnbOutputCell outputContent) {
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      if (replacementContent == null && outputContent == null) {
        myCell.removeCellOutputs();
        myViewPanel.removeAll();
        application.runReadAction(() -> {
          final JComponent panel = createViewPanel();
          myViewPanel.add(panel);
          String prompt = IpnbEditorUtil.prompt(-1, IpnbEditorUtil.PromptType.In);
          myPromptLabel.setText(prompt);
        });
      }

      if (replacementContent != null) {
        myCell.setSource(Arrays.asList(StringUtil.splitByLinesKeepSeparators(replacementContent)));
        String prompt = IpnbEditorUtil.prompt(null, IpnbEditorUtil.PromptType.In);
        myCell.setPromptNumber(null);
        myPromptLabel.setText(prompt);
        application.runWriteAction(() -> myCodeSourcePanel.getEditor().getDocument().setText(replacementContent));
      }
      if (outputContent != null) {
        myCell.addCellOutput(outputContent);
        final JComponent component = myHideableOutputPanel.getSecondComponent();
        if (component != null) {
          addOutputPanel(component, outputContent, outputContent instanceof IpnbOutOutputCell);
        }
      }
    }, ModalityState.stateForComponent(this));
  }

  @Override
  public void updateCellView() {
    myViewPanel.removeAll();
    final JComponent panel = createViewPanel();
    myViewPanel.add(panel);
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
