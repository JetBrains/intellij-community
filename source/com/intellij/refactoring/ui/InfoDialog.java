
package com.intellij.refactoring.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;

public class InfoDialog extends DialogWrapper{
  private JCheckBox myShowInFutureCheckBox;
  private JTextArea myTextArea;
  private String myText;
  private boolean isToShowInFuture;

  public InfoDialog(String text, Project project) {
    super(project, false);
    myText = text;
    setButtonsAlignment(SwingUtilities.CENTER);
    setTitle("Information");
    setButtonsMargin(null);
    init();
    setOKButtonText("OK");
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEtchedBorder());
    panel.setLayout(new BorderLayout());

    JPanel cbPanel = new JPanel(new BorderLayout());
    cbPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    myShowInFutureCheckBox = new JCheckBox("Do not show this message in the future");
    myShowInFutureCheckBox.setMnemonic('D');
    panel.add(cbPanel, BorderLayout.SOUTH);
    cbPanel.add(myShowInFutureCheckBox, BorderLayout.WEST);

    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
    panel.add(textPanel, BorderLayout.CENTER);

    myTextArea = new JTextArea(myText);
    textPanel.add(myTextArea, BorderLayout.CENTER);
    myTextArea.setEditable(false);
    myTextArea.setBackground(UIManager.getColor("Panel.background"));
    Font font = myShowInFutureCheckBox.getFont();
    font = new Font(font.getName(), font.getStyle(), font.getSize() + 1);
    myTextArea.setFont(font);
    myShowInFutureCheckBox.setFont(font);
    isToShowInFuture = true;
    myShowInFutureCheckBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          isToShowInFuture = !myShowInFutureCheckBox.isSelected();
        }
      }
    );
    return panel;
  }

  public boolean isToShowInFuture() {
    return isToShowInFuture;
  }
}
