package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.utils.MavenId;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class MavenArtifactSearchDialog extends DialogWrapper {
  private MavenId myResult;

  private JTabbedPane myTabbedPane;
  private MavenArtifactSearchPanel myArtifactsPanel;
  private MavenArtifactSearchPanel myClassesPanel;

  public static MavenId searchForClass(Project project, String className) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, className, true);
    d.show();
    if (!d.isOK()) return null;

    return d.getResult();
  }

  public static MavenId searchForArtifact(Project project) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, "", false);
    d.show();
    if (!d.isOK()) return null;

    return d.getResult();
  }

  private MavenArtifactSearchDialog(Project project, String initialText, boolean classMode) {
    super(project, true);

    initComponents(project, initialText, classMode);

    setTitle("Maven Artifact Search");
    setOKActionEnabled(false);
    init();

    myArtifactsPanel.scheduleSearch();
    myClassesPanel.scheduleSearch();
  }

  private void initComponents(Project project, String initialText, boolean classMode) {
    myTabbedPane = new JTabbedPane(JTabbedPane.TOP);

    MavenArtifactSearchPanel.Listener l = new MavenArtifactSearchPanel.Listener() {
      public void doubleClicked() {
        clickDefaultButton();
      }

      public void selectedChanged(boolean hasSelection) {
        setOKActionEnabled(hasSelection);
      }

      public void searchFinished() {

      }

      public void searchStarted() {

      }
    };

    myArtifactsPanel = new MavenArtifactSearchPanel(project, !classMode ? initialText : "", false, l);
    myClassesPanel = new MavenArtifactSearchPanel(project, classMode ? initialText : "", true, l);

    myTabbedPane.addTab("Search for artifact", myArtifactsPanel);
    myTabbedPane.addTab("Search for class", myClassesPanel);
    myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

    myTabbedPane.setMnemonicAt(0, KeyEvent.VK_A);
    myTabbedPane.setDisplayedMnemonicIndexAt(0, myTabbedPane.getTitleAt(0).indexOf("artifact"));
    myTabbedPane.setMnemonicAt(1, KeyEvent.VK_C);
    myTabbedPane.setDisplayedMnemonicIndexAt(1, myTabbedPane.getTitleAt(1).indexOf("class"));

    setOKActionEnabled(false);
  }

  @Override
  protected Action getOKAction() {
    Action result = super.getOKAction();
    result.putValue(Action.NAME, "Add");
    return result;
  }

  protected JComponent createCenterPanel() {
    return myTabbedPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTabbedPane.getSelectedIndex() == 0
           ? myArtifactsPanel.getSearchField()
           : myClassesPanel.getSearchField();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Maven.ArtifactSearchDialog";
  }

  public MavenId getResult() {
    return myResult;
  }

  @Override
  protected void doOKAction() {
    MavenArtifactSearchPanel panel = myTabbedPane.getSelectedIndex() == 0 ? myArtifactsPanel : myClassesPanel;
    myResult = panel.getResult();
    super.doOKAction();
  }
}
