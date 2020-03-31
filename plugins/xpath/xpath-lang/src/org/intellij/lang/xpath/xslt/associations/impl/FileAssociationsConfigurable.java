/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.associations.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class FileAssociationsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private final UIState myState;
  private AssociationsEditor myEditor;

  public FileAssociationsConfigurable(Project project) {
    myProject = project;
    myState = ServiceManager.getService(project, UIState.class);
  }

  @Override
  public String getDisplayName() {
    return XPathBundle.message("configurable.FileAssociationsConfigurable.display.name");
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "xslt.associations";
  }

  @Override
  public JComponent createComponent() {
    myEditor = ReadAction.compute(() -> new AssociationsEditor(myProject, myState.state));
    return myEditor.getComponent();
  }

  @Override
  public boolean isModified() {
    return myEditor != null && myEditor.isModified();
  }

  @Override
  public void apply() {
    myEditor.apply();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  public void reset() {
    myEditor.reset();
  }

  @Override
  public void disposeUIResources() {
    if (myEditor != null) {
      myState.state = myEditor.getState();
      myEditor.dispose();
      myEditor = null;
    }
  }

  public AssociationsEditor getEditor() {
    return myEditor;
  }

  public static void editAssociations(Project project, final PsiFile file) {
    final FileAssociationsConfigurable instance = new FileAssociationsConfigurable(project);

    ShowSettingsUtil.getInstance().editConfigurable(project, instance, () -> {
      final AssociationsEditor editor = instance.getEditor();
      if (file != null) {
        editor.select(file);
      }
    });
  }

  @State(name = "XSLT-Support.FileAssociations.UIState", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
  public static class UIState implements PersistentStateComponent<TreeState> {
    private TreeState state;

    @Override
    public TreeState getState() {
      return state != null ? state : TreeState.createFrom(null);
    }

    @Override
    public void loadState(@NotNull TreeState state) {
      this.state = state;
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
