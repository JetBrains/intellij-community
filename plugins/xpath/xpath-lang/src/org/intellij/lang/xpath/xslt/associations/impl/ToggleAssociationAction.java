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
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;

class ToggleAssociationAction extends ToggleAction {
    private final FileAssociationsManager myFileAssociationsManager;
    private final PsiFile myPsiFile;
    private final PsiFile myAssoc;

    ToggleAssociationAction(FileAssociationsManager fileAssociationsManager, PsiFile psiFile, PsiFile assoc) {
        super(getPath(assoc, psiFile), XPathBundle.message("action.remove.association.to.description", assoc.getName()), null);
        myFileAssociationsManager = fileAssociationsManager;
        myPsiFile = psiFile;
        myAssoc = assoc;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    private static @NlsSafe String getPath(PsiFile assoc, PsiFile psiFile) {
        final VirtualFile virtualFile = assoc.getVirtualFile();
        assert virtualFile != null;

        final String path = VfsUtilCore.findRelativePath(psiFile.getVirtualFile(), virtualFile, File.separatorChar);
        final ProjectFileIndex index = ProjectRootManager.getInstance(assoc.getProject()).getFileIndex();
        final Module module = index.getModuleForFile(virtualFile);
        return path != null ? (module != null ? "[" + module.getName() + "] - " + path : path) : virtualFile.getPresentableUrl();
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return true;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        assert !state;
        myFileAssociationsManager.removeAssociation(myPsiFile, myAssoc);
        DaemonCodeAnalyzer.getInstance(AnAction.getEventProject(e)).restart();
    }
}