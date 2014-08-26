package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CodePanel extends IpnbEditablePanel<JComponent, CodeCell> {
  private final Project myProject;
  private final Disposable myParent;
  private CodeSourcePanel myCodeSourcePanel;
  private final List<IpnbPanel> myOutputPanels = Lists.newArrayList();

  public CodePanel(@NotNull final Project project, @Nullable final Disposable parent, @NotNull final CodeCell cell) {
    super(cell);
    myProject = project;
    myParent = parent;
    myCell = cell;

    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  public IpnbFileEditor getFileEditor() {
    assert myParent instanceof IpnbFileEditor;
    return (IpnbFileEditor)myParent;
  }

  public void addPromptPanel(@NotNull final JComponent parent, Integer promptNumber,
                             @NotNull final IpnbEditorUtil.PromptType promptType,
                             IpnbPanel component,
                             GridBagConstraints c) {
    c.gridx = 0;
    c.weightx = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    final JComponent promptComponent = IpnbEditorUtil.createPromptComponent(promptNumber, promptType);
    c.insets = new Insets(2,2,2,5);
    parent.add(promptComponent, c);

    c.gridx = 1;
    c.weightx = 1;
    c.anchor = GridBagConstraints.CENTER;
    parent.add(component, c);
    myOutputPanels.add(component);
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

    myCodeSourcePanel = new CodeSourcePanel(myProject, this, myCell.getSourceAsString());
    if (myParent != null)
      Disposer.register(myParent, new Disposable() {
      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myCodeSourcePanel.getEditor());
      }
    });
    addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel, c);

    c.gridx = 1;

    c.gridy = 0;
    for (CellOutput cellOutput : myCell.getCellOutputs()) {
      c.gridy++;
      addOutputPanel(panel, c, cellOutput, true);
    }
    return panel;
  }

  private void addOutputPanel(@NotNull final JComponent panel, @NotNull final GridBagConstraints c,
                              @NotNull final CellOutput cellOutput, boolean addPrompt) {
    final IpnbEditorUtil.PromptType promptType = addPrompt ? IpnbEditorUtil.PromptType.Out : IpnbEditorUtil.PromptType.None;
    if (cellOutput instanceof ImageCellOutput) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new ImagePanel((ImageCellOutput)cellOutput), c);
    }
    else if (cellOutput instanceof HtmlCellOutput) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new HtmlPanel((HtmlCellOutput)cellOutput), c);
    }
    else if (cellOutput instanceof LatexCellOutput) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new LatexPanel((LatexCellOutput)cellOutput), c);
    }
    else if (cellOutput instanceof ErrorCellOutput) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new ErrorPanel((ErrorCellOutput)cellOutput), c);
    }
    else if (cellOutput instanceof StreamCellOutput) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new StreamPanel((StreamCellOutput)cellOutput), c);
    }
    else if (cellOutput.getSourceAsString() != null) {
      addPromptPanel(panel, myCell.getPromptNumber(), promptType,
                     new CodeOutputPanel(cellOutput.getSourceAsString()), c);
    }
  }

  @Override
  public void switchToEditing() {
    setEditing(true);
    getParent().repaint();
    UIUtil.requestFocus(myCodeSourcePanel.getEditor().getContentComponent());
  }

  @Override
  public void runCell() {
    super.runCell();
    final Document document = myCodeSourcePanel.getEditor().getDocument();
    final String text = document.getText();
    myCell.setSource(StringUtil.splitByLinesKeepSeparators(text));
    final IpnbConnectionManager connectionManager = IpnbConnectionManager.getInstance(myProject);
    connectionManager.executeCell(this);
  }

  public void updatePanel(@NotNull final List<CellOutput> outputContent) {
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

        for (CellOutput output : outputContent) {
          myCell.addCellOutput(output);
          c.gridx = 0;
          c.gridy += 1;

          addOutputPanel(myViewPanel, c, output, output instanceof OutCellOutput);
        }
        revalidate();
        repaint();
      }
    });
  }
}
