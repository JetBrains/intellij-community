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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import org.jdom.Element;

import javax.swing.*;

public class FileAssociationsConfigurable implements ProjectComponent, Configurable, JDOMExternalizable {
    private Project myProject;
    private AssociationsEditor myEditor;
    private TreeState myState;

    FileAssociationsConfigurable(Project project) {
        myProject = project;
    }

    public void readExternal(Element element) throws InvalidDataException {
        final Element child = element.getChild("TreeState");
        if (child != null) {
            myState = new TreeState();
            myState.readExternal(child);
        }
    }

    public void writeExternal(Element element) throws WriteExternalException {
        if (myState != null) {
            final Element child = new Element("TreeState");
            myState.writeExternal(child);
            element.addContent(child);
        }
    }

    public void projectOpened() {
    }

    public void projectClosed() {
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "XSLT-Support.FileAssociationsSettings";
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public String getDisplayName() {
        return "File Associations";
    }

    public Icon getIcon() {
        return IconLoader.findIcon("/icons/association_large.png");
    }

    @Nullable
    public String getHelpTopic() {
        return "xslt.associations";
    }

    public JComponent createComponent() {
        myEditor = new AssociationsEditor(myProject, myState);
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
            myState = myEditor.getState();
            myEditor.dispose();
            myEditor = null;
        }
    }

    public static FileAssociationsConfigurable getInstance(Project project) {
        return project.getComponent(FileAssociationsConfigurable.class);
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
}
