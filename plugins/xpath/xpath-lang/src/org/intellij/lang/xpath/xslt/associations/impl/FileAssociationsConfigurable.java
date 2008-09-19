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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileAssociationsConfigurable implements FileOptionsProvider, NonDefaultProjectConfigurable {
    private final Project myProject;
    private final UIState myState;
    private AssociationsEditor myEditor;

    FileAssociationsConfigurable(Project project) {
        myProject = project;
        myState = ServiceManager.getService(project, UIState.class);
    }

    public String getDisplayName() {
        return "XSLT File Associations";
    }

    public Icon getIcon() {
        return IconLoader.findIcon("/icons/association_large.png");
    }

    @Nullable
    public String getHelpTopic() {
        return "xslt.associations";
    }

    public JComponent createComponent() {
        myEditor = new AssociationsEditor(myProject, myState.state);
        return myEditor.getComponent();
    }

    public synchronized boolean isModified() {
        return myEditor != null && myEditor.isModified();
    }

    public void apply() throws ConfigurationException {
        myEditor.apply();
        DaemonCodeAnalyzer.getInstance(myProject).restart();
    }

    public void reset() {
        myEditor.reset();
    }

    public synchronized void disposeUIResources() {
        if (myEditor != null) {
            myState.state = myEditor.getState();
            myEditor.dispose();
            myEditor = null;
        }
    }

    public static FileAssociationsConfigurable getInstance(Project project) {
        return ShowSettingsUtil.getInstance().findProjectConfigurable(project, FileAssociationsConfigurable.class);
    }

    public AssociationsEditor getEditor() {
        return myEditor;
    }

    public static void editAssociations(Project project, final PsiFile file) {
        final FileAssociationsConfigurable instance = getInstance(project);

        ShowSettingsUtil.getInstance().editConfigurable(project, instance, new Runnable() {
            public void run() {
                final AssociationsEditor editor = instance.getEditor();
                if (file != null) {
                    editor.select(file);
                }
            }
        });
    }

    @State(name = "XSLT-Support.FileAssociations.UIState",
            storages = @Storage(id = "default", file = "$WORKSPACE_FILE$")
    )
    public static class UIState implements PersistentStateComponent<TreeState> {
        private TreeState state;

        public TreeState getState() {
            return state != null ? state : new TreeState();
        }

        public void loadState(TreeState state) {
            this.state = state;
        }
    }
}
