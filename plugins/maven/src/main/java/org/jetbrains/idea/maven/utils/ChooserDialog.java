package org.jetbrains.idea.maven.utils;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class ChooserDialog<T> extends DialogWrapper {
  private final ElementsChooser<T> myChooser;
  private final String myDescription;

  public ChooserDialog(final Project project, ElementsChooser<T> chooser, final String title, final String description) {
    super(project, true);
    myChooser = chooser;
    myChooser.setPreferredSize(new Dimension(300, 150));
    setTitle(title);
    myDescription = description;

    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return new JScrollPane(myChooser);
  }

  protected JComponent createNorthPanel() {
    JTextPane description = new JTextPane();

    JLabel label = new JLabel();
    description.setFont(label.getFont());
    description.setForeground(label.getForeground());
    description.setBackground(UIUtil.getOptionPaneBackground());
    description.setText(myDescription);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(description);
    panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
    return panel;
  }
}
