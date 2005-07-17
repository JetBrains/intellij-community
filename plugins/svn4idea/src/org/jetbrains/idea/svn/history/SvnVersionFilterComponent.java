package org.jetbrains.idea.svn.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;

public class SvnVersionFilterComponent extends StandardVersionFilterComponent {
  private JCheckBox myUseAuthorFilter;
  private JTextField myAuthorField;
  private JPanel myPanel;
  private JPanel myStandardPanel;
  private final Project myProject;

  public SvnVersionFilterComponent(Project project) {
    super(project, new SimpleDateFormat("yyyy/MM/dd hh:mm:ss"));
    myStandardPanel.setLayout(new BorderLayout());
    myStandardPanel.add(getStandardPanel(), BorderLayout.CENTER);
    myProject = project;
    init();
  }

  protected void updateAllEnabled(final ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseAuthorFilter, myAuthorField, e);
  }

  protected void initValues() {
    super.initValues();
    final SvnChangesBrowserSettings settings = SvnChangesBrowserSettings.getSettings(myProject);
    myUseAuthorFilter.setSelected(settings.USE_AUTHOR_FIELD);
    myAuthorField.setText(settings.AUTHOR);
  }

  public void saveValues() {
    super.saveValues();
    final SvnChangesBrowserSettings settings = SvnChangesBrowserSettings.getSettings(myProject);
    settings.AUTHOR = myAuthorField.getText();
    settings.USE_AUTHOR_FIELD = myUseAuthorFilter.isSelected();
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myAuthorField.addActionListener(filterListener);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public String getAuthorFilter() {
    if (myUseAuthorFilter.isSelected() && myAuthorField.getText().length() > 0) {
      return myAuthorField.getText();
    }
    else {
      return null;
    }
  }
}
