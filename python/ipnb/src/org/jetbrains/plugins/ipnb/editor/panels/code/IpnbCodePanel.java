package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class IpnbCodePanel extends IpnbEditablePanel<JComponent, IpnbCodeCell> {
  private final Project myProject;
  @NotNull private final IpnbFileEditor myParent;
  private IpnbCodeSourcePanel myCodeSourcePanel;
  private final List<IpnbPanel> myOutputPanels = Lists.newArrayList();

  public IpnbCodePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new BorderLayout());
    myProject = project;
    myParent = parent;

    myViewPanel = createViewPanel();
    add(myViewPanel);
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
    if (component instanceof IpnbPanel)
      myOutputPanels.add((IpnbPanel)component);
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel mainPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, true, false));
    mainPanel.setBackground(IpnbEditorUtil.getBackground());

    myCodeSourcePanel = new IpnbCodeSourcePanel(myProject, this, myCell);
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());
    addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel);
    mainPanel.add(panel);

    for (IpnbOutputCell outputCell : myCell.getCellOutputs()) {
      addOutputPanel(mainPanel, outputCell, true);
    }
    return mainPanel;
  }

  private void addOutputPanel(@NotNull final JComponent mainPanel,
                              @NotNull final IpnbOutputCell outputCell, boolean addPrompt) {
    final IpnbEditorUtil.PromptType promptType = addPrompt ? IpnbEditorUtil.PromptType.Out : IpnbEditorUtil.PromptType.None;
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());
    if (outputCell instanceof IpnbImageOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbImagePanel((IpnbImageOutputCell)outputCell));
    }
    else if (outputCell instanceof IpnbHtmlOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbHtmlPanel((IpnbHtmlOutputCell)outputCell, myParent.getIpnbFilePanel()));
    }
    else if (outputCell instanceof IpnbLatexOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbLatexPanel((IpnbLatexOutputCell)outputCell, myParent.getIpnbFilePanel()));
    }
    else if (outputCell instanceof IpnbErrorOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbErrorPanel((IpnbErrorOutputCell)outputCell));
    }
    else if (outputCell instanceof IpnbStreamOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.None,
                     new IpnbStreamPanel((IpnbStreamOutputCell)outputCell));
    }
    else if (outputCell.getSourceAsString() != null) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbCodeOutputPanel<>(outputCell, myParent.getIpnbFilePanel()));
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
  public void runCell() {
    super.runCell();
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
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (replacementContent != null) {
          myCell.setSource(Arrays.asList(StringUtil.splitByLinesKeepSeparators(replacementContent)));
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              myCodeSourcePanel.getEditor().getDocument().setText(replacementContent);
            }
          });
        }
        myCell.removeCellOutputs();
        myViewPanel.removeAll();

        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(IpnbEditorUtil.getBackground());
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel);
        myViewPanel.add(panel);
        isRunning = false;
        if (outputContent != null) {
          for (IpnbOutputCell output : outputContent) {
            myCell.addCellOutput(output);
            addOutputPanel(myViewPanel, output, true);
          }
        }

        final IpnbFilePanel filePanel = myParent.getIpnbFilePanel();
        filePanel.revalidate();
        filePanel.repaint();
      }
    });
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
  @Override
  protected Object clone() {
    return new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)myCell.clone());
  }
}
