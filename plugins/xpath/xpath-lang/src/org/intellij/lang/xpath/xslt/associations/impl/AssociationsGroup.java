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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import icons.XpathIcons;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssociationsGroup extends ActionGroup {

    public AssociationsGroup() {
        getTemplatePresentation().setIcon(XpathIcons.Association);
    }

    @NotNull
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        if (!isEnabled(e)) return AnAction.EMPTY_ARRAY;

        final Project project = getEventProject(e);
        if (project == null) return AnAction.EMPTY_ARRAY;
        final PsiFile psiFile = getPsiFile(e);
        if (psiFile == null) return AnAction.EMPTY_ARRAY;

        final FileAssociationsManager fileAssociationsManager = FileAssociationsManager.getInstance(project);
        final PsiFile[] associationsFor = fileAssociationsManager.getAssociationsFor(psiFile, FileAssociationsManager.Holder.XML_FILES);
        final AnAction[] children;
        if (associationsFor.length == 0) {
            children = new AnAction[2];
        } else {
            children = new AnAction[associationsFor.length + 3];
            for (int i = 0; i < associationsFor.length; i++) {
                PsiFile assoc = associationsFor[i];
                children[i] = new ToggleAssociationAction(fileAssociationsManager, psiFile, assoc);
            }
            children[children.length - 3] = Separator.getInstance();
        }
        children[children.length - 2] = new AddAssociationAction(fileAssociationsManager);
        children[children.length - 1] = new ConfigureAssociationsAction();
        return children;
    }

    public void update(AnActionEvent e) {
        e.getPresentation().setVisible(isVisible(e));
        e.getPresentation().setEnabled(isEnabled(e));
    }

    private static boolean isEnabled(@Nullable AnActionEvent e) {
        if (e == null) return false;
        final PsiFile psiFile = getPsiFile(e);
        if (psiFile == null) return false;
        if (!XsltSupport.isXsltFile(psiFile)) return false;
        final Project project = getEventProject(e);
        if (project == null) return false;
        return PsiManager.getInstance(project).isInProject(psiFile);
    }

    private static boolean isVisible(AnActionEvent e) {
        final PsiFile psiFile = getPsiFile(e);
        if (psiFile == null) return false;
        return XsltSupport.isXsltFile(psiFile);
    }

    @Nullable
    static PsiFile getPsiFile(@Nullable AnActionEvent e) {
        return e != null ? CommonDataKeys.PSI_FILE.getData(e.getDataContext()) : null;
    }
}
