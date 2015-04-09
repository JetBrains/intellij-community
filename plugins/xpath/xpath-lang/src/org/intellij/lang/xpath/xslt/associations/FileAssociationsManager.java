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
package org.intellij.lang.xpath.xslt.associations;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class FileAssociationsManager extends SimpleModificationTracker {
    public static class Holder {
        public static final FileType[] XML_FILES = {StdFileTypes.XML, StdFileTypes.XHTML};
        public static final List<FileType> XML_FILES_LIST = Arrays.asList(XML_FILES);
    }

    public abstract void removeAssociations(PsiFile file);

    public abstract void removeAssociation(PsiFile file, PsiFile assoc);

    public abstract void addAssociation(PsiFile file, PsiFile assoc);

    public abstract void addAssociation(PsiFile file, VirtualFile assoc);

    public abstract Map<VirtualFile, VirtualFile[]> getAssociations();

    public abstract PsiFile[] getAssociationsFor(PsiFile file);

    public abstract PsiFile[] getAssociationsFor(PsiFile file, FileType... fileTypes);

    public static FileAssociationsManager getInstance(Project project) {
        return project.getComponent(FileAssociationsManager.class);
    }
}