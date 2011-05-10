package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;
import java.awt.*;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionPanel extends JPanel {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myBrowseTargetFileButton;
  private JLabel myElementsToMove;

  public PyMoveClassOrFunctionPanel(String elementsToMoveText, String initialTargetFile) {
    super();
    this.setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    myElementsToMove.setText(elementsToMoveText);
    myBrowseTargetFileButton.setText(initialTargetFile);
  }

  public TextFieldWithBrowseButton getBrowseTargetFileButton() {
    return myBrowseTargetFileButton;
  }
}
