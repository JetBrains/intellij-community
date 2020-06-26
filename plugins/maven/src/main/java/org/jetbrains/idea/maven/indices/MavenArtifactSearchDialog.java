// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.model.MavenId;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.*;

public final class MavenArtifactSearchDialog extends DialogWrapper {
  private List<MavenId> myResult = Collections.emptyList();

  public static List<MavenId> ourResultForTest;

  private TabbedPaneWrapper myTabbedPane;
  private MavenArtifactSearchPanel myArtifactsPanel;
  private MavenArtifactSearchPanel myClassesPanel;

  private final Map<Pair<String, String>, String> myManagedDependenciesMap = new HashMap<>();

  private final Map<MavenArtifactSearchPanel, Boolean> myOkButtonStates = new THashMap<>();

  @NotNull
  public static List<MavenId> searchForClass(Project project, String className) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      assert ourResultForTest != null;

      List<MavenId> res = ourResultForTest;
      ourResultForTest = null;
      return res;
    }

    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, className, true);
    if (!d.showAndGet()) {
      return Collections.emptyList();
    }

    return d.getResult();
  }

  @NotNull
  public static List<MavenId> searchForArtifact(Project project, Collection<MavenDomDependency> managedDependencies) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      assert ourResultForTest != null;

      List<MavenId> res = ourResultForTest;
      ourResultForTest = null;
      return res;
    }

    MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, "", false);
    d.setManagedDependencies(managedDependencies);

    if (!d.showAndGet()) {
      return Collections.emptyList();
    }

    return d.getResult();
  }

  public void setManagedDependencies(Collection<MavenDomDependency> managedDependencies) {
    myManagedDependenciesMap.clear();

    for (MavenDomDependency dependency : managedDependencies) {
      String groupId = dependency.getGroupId().getStringValue();
      String artifactId = dependency.getArtifactId().getStringValue();
      String version = dependency.getVersion().getStringValue();

      if (StringUtil.isNotEmpty(groupId) && StringUtil.isNotEmpty(artifactId) && StringUtil.isNotEmpty(version)) {
        myManagedDependenciesMap.put(Pair.create(groupId, artifactId), version);
      }
    }
  }

  private MavenArtifactSearchDialog(Project project, String initialText, boolean classMode) {
    super(project, true);

    initComponents(project, initialText, classMode);

    setTitle(MavenDomBundle.message("maven.artifact.pom.search.title"));
    updateOkButtonState();
    init();

    myArtifactsPanel.scheduleSearch();
    myClassesPanel.scheduleSearch();
  }

  private void initComponents(Project project, String initialText, boolean classMode) {
    myTabbedPane = new TabbedPaneWrapper(getDisposable());

    MavenArtifactSearchPanel.Listener listener = new MavenArtifactSearchPanel.Listener() {
      @Override
      public void itemSelected() {
        clickDefaultButton();
      }

      @Override
      public void canSelectStateChanged(@NotNull MavenArtifactSearchPanel from, boolean canSelect) {
        myOkButtonStates.put(from, canSelect);
        updateOkButtonState();
      }
    };

    myArtifactsPanel = new MavenArtifactSearchPanel(project, !classMode ? initialText : "", false, listener, this, myManagedDependenciesMap);
    myClassesPanel = new MavenArtifactSearchPanel(project, classMode ? initialText : "", true, listener, this, myManagedDependenciesMap);

    myTabbedPane.addTab(MavenDomBundle.message("maven.search.for.artifact.tab.title"), myArtifactsPanel);
    myTabbedPane.addTab(MavenDomBundle.message("maven.search.for.class.tab.title"), myClassesPanel);
    myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

    myTabbedPane.getComponent().setPreferredSize(JBUI.size(900, 600));

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
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
    result.putValue(Action.NAME, MavenDomBundle.message("maven.artifact.pom.search.add"));
    return result;
  }

  @Override
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
