/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.documentation.DocumentationComponent.COLOR_KEY;

@State(name = "PySciProjectComponent", storages = @Storage("other.xml"))
public class PySciProjectComponent extends AbstractProjectComponent implements PersistentStateComponent<PySciProjectComponent.State> {
  private State myState = new State();

  protected PySciProjectComponent(Project project) {
    super(project);
  }

  public static PySciProjectComponent getInstance(Project project) {
    return project.getComponent(PySciProjectComponent.class);
  }

  public void useSciView(boolean useSciView) {
    myState.PY_SCI_VIEW = useSciView;
  }

  public void sciViewSuggested(boolean suggested) {
    myState.PY_SCI_VIEW_SUGGESTED = suggested;
  }

  public boolean sciViewSuggested() {
    return myState.PY_SCI_VIEW_SUGGESTED;
  }

  public boolean useSciView() {
    return myState.PY_SCI_VIEW;
  }

  @Override
  public void projectOpened() {
    final VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myState.PY_SCI_VIEW) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        scheme.setColor(COLOR_KEY, UIUtil.getEditorPaneBackground());

        final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(baseDir);
        if (directory != null) {
          DocumentationManager.getInstance(myProject).showJavaDocInfo(directory, directory);
        }
      });
    }
  }

  @Nullable
  @Override
  public PySciProjectComponent.State getState() {
    return myState;
  }

  @Override
  public void loadState(PySciProjectComponent.State state) {
    myState.PY_SCI_VIEW = state.PY_SCI_VIEW;
    myState.PY_SCI_VIEW_SUGGESTED = state.PY_SCI_VIEW_SUGGESTED;
  }

  public static class State {
    public boolean PY_SCI_VIEW = false;
    public boolean PY_SCI_VIEW_SUGGESTED = false;
  }
}
