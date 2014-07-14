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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IconUtil;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;

class AddAssociationAction extends AnAction {
  private final FileAssociationsManager myManager;

    public AddAssociationAction(FileAssociationsManager manager) {
        super("Add...", "Add File Association", IconUtil.getAddIcon());
        myManager = manager;
    }

    public void actionPerformed(AnActionEvent e) {
        final PsiFile psiFile = AssociationsGroup.getPsiFile(e);
        if (psiFile == null) return;

        addAssociation(psiFile);
        DaemonCodeAnalyzer.getInstance(psiFile.getProject()).restart(psiFile);
    }

    protected void addAssociation(final PsiFile psiFile) {

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        assert virtualFile != null;

        final FileChooserDescriptor descriptor = new AnyXMLDescriptor(true) {
            public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) && !file.equals(virtualFile);
            }
        };

        final VirtualFile[] virtualFiles = FileChooser.chooseFiles(descriptor, psiFile.getProject(), psiFile.getVirtualFile());
        if (virtualFiles.length == 0) return; // cancel

        for (VirtualFile file : virtualFiles) {
            assert !virtualFile.equals(file);
            myManager.addAssociation(psiFile, file);
        }
    }
}
