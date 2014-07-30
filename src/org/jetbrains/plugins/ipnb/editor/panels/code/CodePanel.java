package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.HtmlCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.ImageCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.LatexCellOutput;

import javax.swing.*;
import java.awt.*;

public class CodePanel extends IpnbPanel {

  private final Project myProject;
  private final Disposable myParent;
  @NotNull private final CodeCell myCell;
  private CodeSourcePanel myCodeSourcePanel;

  public CodePanel(@NotNull final Project project, @NotNull final Disposable parent, @NotNull final CodeCell cell) {
    super();
    myProject = project;
    myParent = parent;
    myCell = cell;

    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  public void addPromptPanel(@NotNull final JPanel parent, int promptNumber,
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

    myCodeSourcePanel = new CodeSourcePanel(myProject, myParent, myCell.getSourceAsString());
    addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.In, myCodeSourcePanel, c);

    c.gridx = 1;

    c.gridy = 0;
    for (CellOutput cellOutput : myCell.getCellOutputs()) {
      c.gridy++;
      if (cellOutput instanceof ImageCellOutput) {
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new ImagePanel((ImageCellOutput)cellOutput), c);
      }
      else if (cellOutput instanceof HtmlCellOutput) {
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new HtmlPanel((HtmlCellOutput)cellOutput), c);
      }
      else if (cellOutput instanceof LatexCellOutput) {
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new LatexPanel((LatexCellOutput)cellOutput), c);
      }
      else if (cellOutput.getSourceAsString() != null) {
        addPromptPanel(panel, myCell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new CodeOutputPanel(cellOutput.getSourceAsString()), c);
      }
    }
    return panel;
  }

  @Override
  public void switchToEditing() {
    super.switchToEditing();
    UIUtil.requestFocus(myCodeSourcePanel.getEditor().getContentComponent());
  }
}
