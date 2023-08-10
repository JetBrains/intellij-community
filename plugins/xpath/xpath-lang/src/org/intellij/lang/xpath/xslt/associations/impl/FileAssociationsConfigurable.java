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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class FileAssociationsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final @NotNull Project myProject;
  private final @NotNull UIState myState;
  private AssociationsEditor myEditor;

  public FileAssociationsConfigurable(@NotNull Project project) {
    myProject = project;
    myState = project.getService(UIState.class);
  }

  @Override
  public @NotNull @Nls String getDisplayName() {
    return XPathBundle.message("configurable.FileAssociationsConfigurable.display.name");
  }

  @Override
  public @NotNull @NonNls String getHelpTopic() {
    return "xslt.associations";
  }

  @Override
  public @NotNull JComponent createComponent() {
    myEditor = new AssociationsEditor(myProject, myState.state);
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
      Disposer.dispose(myEditor);
      myEditor = null;
    }
  }

  public static void editAssociations(@NotNull Project project,
                                      @Nullable PsiFile file) {
    FileAssociationsConfigurable instance = new FileAssociationsConfigurable(project);

    ShowSettingsUtil.getInstance().editConfigurable(project, instance, () -> {
      if (file != null) {
        instance.myEditor.select(file);
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
