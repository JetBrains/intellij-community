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
import com.intellij.ui.TabbedPaneWrapper;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenArtifactSearchDialog extends DialogWrapper {
  private List<MavenId> myResult = Collections.emptyList();

  private TabbedPaneWrapper myTabbedPane;
  private MavenArtifactSearchPanel myArtifactsPanel;
  private MavenArtifactSearchPanel myClassesPanel;

  private final Map<MavenArtifactSearchPanel, Boolean> myOkButtonStates = new THashMap<MavenArtifactSearchPanel, Boolean>();

  @NotNull
  public static List<MavenId> searchForClass(Project project, String className) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, className, true);
    d.show();
    if (!d.isOK()) return Collections.emptyList();

    return d.getResult();
  }

  @NotNull
  public static List<MavenId> searchForArtifact(Project project) {
    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, "", false);
    d.show();
    if (!d.isOK()) return Collections.emptyList();

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
    myTabbedPane = new TabbedPaneWrapper(project);

    MavenArtifactSearchPanel.Listener listener = new MavenArtifactSearchPanel.Listener() {
      public void itemSelected() {
        clickDefaultButton();
      }

      public void canSelectStateChanged(MavenArtifactSearchPanel from, boolean canSelect) {
        myOkButtonStates.put(from, canSelect);
        updateOkButtonState();
      }
    };

    myArtifactsPanel = new MavenArtifactSearchPanel(project, !classMode ? initialText : "", false, listener, this);
    myClassesPanel = new MavenArtifactSearchPanel(project, classMode ? initialText : "", true, listener, this);

    myTabbedPane.addTab("Search for artifact", myArtifactsPanel);
    myTabbedPane.addTab("Search for class", myClassesPanel);
    myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

    myTabbedPane.getComponent().setPreferredSize(new Dimension(900, 600));

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

  @NotNull
  @Override
  protected Action getOKAction() {
    Action result = super.getOKAction();
    result.putValue(Action.NAME, "Add");
    return result;
  }

  protected JComponent createCenterPanel() {
    return myTabbedPane.getComponent();
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

  @NotNull
  public List<MavenId> getResult() {
    return myResult;
  }

  @Override
  protected void doOKAction() {
    MavenArtifactSearchPanel panel = myTabbedPane.getSelectedIndex() == 0 ? myArtifactsPanel : myClassesPanel;
    myResult = panel.getResult();
    super.doOKAction();
  }
}
