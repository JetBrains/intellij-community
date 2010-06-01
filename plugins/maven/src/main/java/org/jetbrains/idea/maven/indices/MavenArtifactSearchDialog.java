/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.model.MavenId;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.KeyEvent;
import java.awt.*;
import java.util.Map;

public class MavenArtifactSearchDialog extends DialogWrapper {
  private MavenId myResult;

  private JTabbedPane myTabbedPane;
  private MavenArtifactSearchPanel myArtifactsPanel;
  private MavenArtifactSearchPanel myClassesPanel;

  private final Map<MavenArtifactSearchPanel, Boolean> myOkButtonStates = new THashMap<MavenArtifactSearchPanel, Boolean>();

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
    updateOkButtonState();
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

      public void canSelectStateChanged(MavenArtifactSearchPanel from, boolean canSelect) {
        myOkButtonStates.put(from, canSelect);
        updateOkButtonState();
      }
    };

    myArtifactsPanel = new MavenArtifactSearchPanel(project, !classMode ? initialText : "", false, l,getDisposable());
    myClassesPanel = new MavenArtifactSearchPanel(project, classMode ? initialText : "", true, l,getDisposable());

    myTabbedPane.addTab("Search for artifact", myArtifactsPanel);
    myTabbedPane.addTab("Search for class", myClassesPanel);
    myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

    myTabbedPane.setMnemonicAt(0, KeyEvent.VK_A);
    myTabbedPane.setDisplayedMnemonicIndexAt(0, myTabbedPane.getTitleAt(0).indexOf("artifact"));
    myTabbedPane.setMnemonicAt(1, KeyEvent.VK_C);
    myTabbedPane.setDisplayedMnemonicIndexAt(1, myTabbedPane.getTitleAt(1).indexOf("class"));

    myTabbedPane.setPreferredSize(new Dimension(600, 400));

    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateOkButtonState();
      }
    });

    updateOkButtonState();
  }

  private void updateOkButtonState() {
    Boolean canSelect = myOkButtonStates.get(myTabbedPane.getSelectedComponent());
    if (canSelect == null) canSelect = false;
    setOKActionEnabled(canSelect);
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
