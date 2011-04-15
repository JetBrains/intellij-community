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

import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

class AddAssociationAction extends AnAction {
    private final FileAssociationsManager myManager;

    public AddAssociationAction(FileAssociationsManager manager) {
        super("Add...", "Add File Association", IconLoader.getIcon("/general/add.png"));
        myManager = manager;
    }

    public void actionPerformed(AnActionEvent e) {
        final PsiFile psiFile = AssociationsGroup.getPsiFile(e);
        if (psiFile == null) return;
        final Project project = LangDataKeys.PROJECT.getData(e.getDataContext());
        if (project == null) return;

        addAssociation(e, psiFile);
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    protected void addAssociation(AnActionEvent e, PsiFile psiFile) {
        final Project project = AssociationsGroup.getEventProject(e);
        if (project == null) return;

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        assert virtualFile != null;

        final FileChooserDescriptor descriptor = new AnyXMLDescriptor() {
            public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) && !file.equals(virtualFile);
            }
        };

        final VirtualFile[] virtualFiles = FileChooser.chooseFiles(project, descriptor, psiFile.getVirtualFile());
        if (virtualFiles.length == 0) return; // cancel

        for (VirtualFile file : virtualFiles) {
            assert !virtualFile.equals(file);
            myManager.addAssociation(psiFile, file);
        }
    }

}
