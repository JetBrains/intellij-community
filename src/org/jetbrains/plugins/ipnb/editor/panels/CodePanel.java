package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.HtmlCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.ImageCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.LatexCellOutput;

import javax.swing.*;
import java.awt.*;

public class CodePanel extends IpnbPanel {

  public CodePanel(Project project, Disposable parent, @NotNull final CodeCell cell) {
    super(new GridBagLayout());
    setBackground(IpnbEditorUtil.getBackground());

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;

    addPromptPanel(cell.getPromptNumber(), IpnbEditorUtil.PromptType.In, new CodeSourcePanel(project, parent, cell.getSourceAsString()), c);

    c.gridx = 1;

    c.gridy = 0;
    for (CellOutput cellOutput : cell.getCellOutputs()) {
      c.gridy++;
      if (cellOutput instanceof ImageCellOutput) {
        addPromptPanel(cell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new ImagePanel(project, (ImageCellOutput)cellOutput), c);
      }
      else if (cellOutput instanceof HtmlCellOutput) {
        addPromptPanel(cell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new HtmlPanel(project, (HtmlCellOutput)cellOutput), c);
      }
      else if (cellOutput instanceof LatexCellOutput) {
        addPromptPanel(cell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new LatexPanel(project, (LatexCellOutput)cellOutput), c);
      }
      else if (cellOutput.getSourceAsString() != null) {
        addPromptPanel(cell.getPromptNumber(), IpnbEditorUtil.PromptType.Out,
                       new CodeOutputPanel(cellOutput.getSourceAsString()), c);
      }
    }
  }



  public void addPromptPanel(int promptNumber,
                             @NotNull final IpnbEditorUtil.PromptType promptType,
                             IpnbPanel component,
                             GridBagConstraints c) {
    c.gridx = 0;
    c.weightx = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    final JComponent promptComponent = IpnbEditorUtil.createPromptComponent(promptNumber, promptType);
    c.insets = new Insets(2,2,2,5);
    add(promptComponent, c);

    c.gridx = 1;
    c.weightx = 1;
    c.anchor = GridBagConstraints.CENTER;
    add(component, c);

  }

}
