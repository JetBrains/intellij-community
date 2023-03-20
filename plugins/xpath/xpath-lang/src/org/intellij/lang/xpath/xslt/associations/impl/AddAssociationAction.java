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
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IconUtil;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AddAssociationAction extends AnAction {
  private final FileAssociationsManager myManager;

    AddAssociationAction(FileAssociationsManager manager) {
        super(XPathBundle.message("action.add.association.text"),
              XPathBundle.message("action.add.file.association.description"),
              IconUtil.getAddIcon());
        myManager = manager;
    }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final PsiFile psiFile = AssociationsGroup.getPsiFile(e);
        if (psiFile == null) return;

        addAssociation(psiFile);
        DaemonCodeAnalyzer.getInstance(psiFile.getProject()).restart(psiFile);
    }

    protected void addAssociation(final PsiFile psiFile) {

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        assert virtualFile != null;

        final FileChooserDescriptor descriptor = new AnyXMLDescriptor(true) {
            @Override
            public boolean isFileSelectable(@Nullable VirtualFile file) {
                return super.isFileSelectable(file) && !file.equals(virtualFile);
            }
        };

        final VirtualFile[] virtualFiles = FileChooser.chooseFiles(descriptor, psiFile.getProject(), psiFile.getVirtualFile());

        for (VirtualFile file : virtualFiles) {
            assert !virtualFile.equals(file);
            myManager.addAssociation(psiFile, file);
        }
    }
}
