package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
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
import java.util.List;

public class IpnbCodePanel extends IpnbEditablePanel<JComponent, IpnbCodeCell> {
  private final Project myProject;
  private final IpnbFileEditor myParent;
  private IpnbCodeSourcePanel myCodeSourcePanel;
  private final List<IpnbPanel> myOutputPanels = Lists.newArrayList();

  public IpnbCodePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new BorderLayout());
    myProject = project;
    myParent = parent;

    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  public IpnbFileEditor getFileEditor() {
    return myParent;
  }

  public Editor getEditor() {
    return myCodeSourcePanel.getEditor();
  }

  public void addPromptPanel(@NotNull final JComponent parent, Integer promptNumber,
                             @NotNull final IpnbEditorUtil.PromptType promptType,
                             @NotNull final JComponent component, @NotNull final GridBagConstraints c) {
    super.addPromptPanel(parent, promptNumber, promptType, component, c);
    if (component instanceof IpnbPanel)
      myOutputPanels.add((IpnbPanel)component);
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(IpnbEditorUtil.getBackground());

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;

    myCodeSourcePanel = new IpnbCodeSourcePanel(myProject, this, myCell);
    addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel, c);

    c.gridx = 1;

    c.gridy = 0;
    for (IpnbOutputCell outputCell : myCell.getCellOutputs()) {
      c.gridy++;
      addOutputPanel(panel, c, outputCell, true);
    }
    return panel;
  }

  private void addOutputPanel(@NotNull final JComponent panel, @NotNull final GridBagConstraints c,
                              @NotNull final IpnbOutputCell outputCell, boolean addPrompt) {
    final IpnbEditorUtil.PromptType promptType = addPrompt ? IpnbEditorUtil.PromptType.Out : IpnbEditorUtil.PromptType.None;
    if (outputCell instanceof IpnbImageOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbImagePanel((IpnbImageOutputCell)outputCell), c);
    }
    else if (outputCell instanceof IpnbHtmlOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbHtmlPanel((IpnbHtmlOutputCell)outputCell), c);
    }
    else if (outputCell instanceof IpnbLatexOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbLatexPanel((IpnbLatexOutputCell)outputCell), c);
    }
    else if (outputCell instanceof IpnbErrorOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbErrorPanel((IpnbErrorOutputCell)outputCell), c);
    }
    else if (outputCell instanceof IpnbStreamOutputCell) {
      addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.None,
                     new IpnbStreamPanel((IpnbStreamOutputCell)outputCell), c);
    }
    else if (outputCell.getSourceAsString() != null) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new IpnbCodeOutputPanel<IpnbOutputCell>(outputCell), c);
    }
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
    myCell.setPromptNumber(-1);
    updatePanel(myCell.getCellOutputs());
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
    myCell.setSource(StringUtil.splitByLinesKeepSeparators(text));
  }

  public void updatePanel(@NotNull final List<IpnbOutputCell> outputContent) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myCell.removeCellOutputs();

        myViewPanel.removeAll();
        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;

        addPromptPanel(myViewPanel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel, c);

        for (IpnbOutputCell output : outputContent) {
          myCell.addCellOutput(output);
          c.gridx = 0;
          c.gridy += 1;

          addOutputPanel(myViewPanel, c, output, output instanceof IpnbOutOutputCell);
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
