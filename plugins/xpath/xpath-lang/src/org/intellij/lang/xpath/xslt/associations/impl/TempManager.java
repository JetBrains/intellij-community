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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;

import java.util.Map;

class TempManager extends TransactionalManager {
    private final FileAssociationsManagerImpl myImpl;
    private final FileAssociationsManagerImpl myTmp;
    private long myStartModCount;
    private long myImplModCount;

    TempManager(FileAssociationsManagerImpl impl, Project project, VirtualFilePointerManager filePointerManager) {
        myTmp = new FileAssociationsManagerImpl(project, filePointerManager);
        myTmp.markAsTempCopy();
        myTmp.copyFrom(impl);

        myImpl = impl;
        myStartModCount = 0;
        myImplModCount = myImpl.getModificationCount();
    }

    public void applyChanges() {
        assert myImplModCount == myImpl.getModificationCount();

        myImpl.copyFrom(myTmp);
        myImplModCount = myImpl.getModificationCount();
        myStartModCount = myTmp.getModificationCount();
    }

    public boolean isModified() {
        assert myImplModCount == myImpl.getModificationCount();

        return myStartModCount != myTmp.getModificationCount();
    }

    public void reset() {
        assert myImplModCount == myImpl.getModificationCount();

        myTmp.copyFrom(myImpl);
        myStartModCount = myTmp.getModificationCount();
    }

    public void dispose() {
        myTmp.disposeComponent();
    }

    public void removeAssociations(PsiFile file) {
        myTmp.removeAssociations(file);
    }

    public void removeAssociation(PsiFile file, PsiFile assoc) {
        myTmp.removeAssociation(file, assoc);
    }

    public void addAssociation(PsiFile file, PsiFile assoc) {
        myTmp.addAssociation(file, assoc);
    }

    public void addAssociation(PsiFile file, VirtualFile assoc) {
        myTmp.addAssociation(file, assoc);
    }

    public Map<VirtualFile, VirtualFile[]> getAssociations() {
        return myTmp.getAssociations();
    }

    public PsiFile[] getAssociationsFor(PsiFile file) {
        return myTmp.getAssociationsFor(file);
    }

    public PsiFile[] getAssociationsFor(PsiFile file, FileType... fileTypes) {
        return myTmp.getAssociationsFor(file, fileTypes);
    }
}
