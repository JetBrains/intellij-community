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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class FileAssociationsManagerImpl extends FileAssociationsManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(FileAssociationsManagerImpl.class);

  private final Project myProject;
  private final VirtualFilePointerManager myFilePointerManager;
  private final Map<VirtualFilePointer, VirtualFilePointerContainer> myAssociations;
  private boolean myTempCopy;

  public FileAssociationsManagerImpl(Project project, VirtualFilePointerManager filePointerManager) {
    myProject = project;
    myFilePointerManager = filePointerManager;
    myAssociations = new LinkedHashMap<>();
  }
  
  public void markAsTempCopy() {
    myTempCopy = true;
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    final List<Element> children = element.getChildren("file");
    for (Element child : children) {
      final String url = child.getAttributeValue("url");
      if (url != null) {
        final VirtualFilePointer pointer = myFilePointerManager.create(url, myProject, null);
        final VirtualFilePointerContainer container = myFilePointerManager.createContainer(myProject);
        container.readExternal(child, "association");
        myAssociations.put(pointer, container);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      final Element e = new Element("file");
      e.setAttribute("url", pointer.getUrl());
      final VirtualFilePointerContainer container = myAssociations.get(pointer);
      container.writeExternal(e, "association");
      element.addContent(e);
    }
  }

  public TransactionalManager getTempManager() {
    return new TempManager(this, myProject, myFilePointerManager);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "XSLT-Support.FileAssociationsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    clear();
  }

  private void clear() {
    final Set<VirtualFilePointer> virtualFilePointers = myAssociations.keySet();
    for (VirtualFilePointer pointer : virtualFilePointers) {
      myAssociations.get(pointer).killAll();
    }
    myAssociations.clear();
  }

  void copyFrom(FileAssociationsManagerImpl source) {
    clear();
    myAssociations.putAll(copy(source));
    touch();
  }

  private void touch() {
    incModificationCount();
    if (!myTempCopy) {
      final ProjectView view = ProjectView.getInstance(myProject);
      if (view != null) {
        view.refresh();
      }
    }
  }

  private static HashMap<VirtualFilePointer, VirtualFilePointerContainer> copy(FileAssociationsManagerImpl other) {
    final HashMap<VirtualFilePointer, VirtualFilePointerContainer> hashMap = new LinkedHashMap<>();

    final Set<VirtualFilePointer> virtualFilePointers = other.myAssociations.keySet();
    for (VirtualFilePointer pointer : virtualFilePointers) {
      final VirtualFilePointerContainer container = other.myFilePointerManager.createContainer(other.myProject);
      container.addAll(other.myAssociations.get(pointer));
      hashMap.put(other.myFilePointerManager.duplicate(pointer, other.myProject, null), container);
    }
    return hashMap;
  }

  public void removeAssociations(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;

    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.getUrl().equals(virtualFile.getUrl())) {
        myAssociations.remove(pointer);
        touch();
        return;
      }
    }
  }

  public void removeAssociation(PsiFile file, PsiFile assoc) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    if (assoc.getVirtualFile() == null) return;

    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.getUrl().equals(virtualFile.getUrl())) {
        VirtualFilePointerContainer container = myAssociations.get(pointer);
        if (container != null) {
          //noinspection ConstantConditions
          final VirtualFilePointer p = container.findByUrl(assoc.getVirtualFile().getUrl());
          if (p != null) {
            container.remove(p);
            if (container.size() == 0) {
              myAssociations.remove(pointer);
            }
            touch();
          }
        }
        return;
      }
    }
  }

  public void addAssociation(PsiFile file, PsiFile assoc) {
    final VirtualFile virtualFile = assoc.getVirtualFile();
    if (virtualFile == null) {
      LOG.warn("No VirtualFile for " + file.getName());
      return;
    }
    addAssociation(file, virtualFile);
  }

  public void addAssociation(PsiFile file, VirtualFile assoc) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      LOG.warn("No VirtualFile for " + file.getName());
      return;
    }

    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.getUrl().equals(virtualFile.getUrl())) {
        VirtualFilePointerContainer container = myAssociations.get(pointer);
        if (container == null) {
          container = myFilePointerManager.createContainer(myProject);
          myAssociations.put(pointer, container);
        }
        if (container.findByUrl(assoc.getUrl()) == null) {
          container.add(assoc);
          touch();
        }
        return;
      }
    }
    final VirtualFilePointerContainer container = myFilePointerManager.createContainer(myProject);
    container.add(assoc);
    myAssociations.put(myFilePointerManager.create(virtualFile, myProject, null), container);
    touch();
  }

  public Map<VirtualFile, VirtualFile[]> getAssociations() {
    final HashMap<VirtualFile, VirtualFile[]> map = new LinkedHashMap<>();
    final Set<VirtualFilePointer> set = myAssociations.keySet();
    for (VirtualFilePointer pointer : set) {
      if (pointer.isValid()) {
        final VirtualFile file = pointer.getFile();
        map.put(file, myAssociations.get(pointer).getFiles());
      }
    }
    return map;
  }

  public PsiFile[] getAssociationsFor(PsiFile file) {
    return getAssociationsFor(file, FileType.EMPTY_ARRAY);
  }

  public PsiFile[] getAssociationsFor(PsiFile file, FileType... fileTypes) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return PsiFile.EMPTY_ARRAY;

    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.isValid() && pointer.getUrl().equals(virtualFile.getUrl())) {
        final VirtualFilePointerContainer container = myAssociations.get(pointer);
        if (container != null) {
          final VirtualFile[] files = container.getFiles();
          final Set<PsiFile> list = new HashSet<>();
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          for (VirtualFile assoc : files) {
            final PsiFile psiFile = psiManager.findFile(assoc);
            if (psiFile != null && (fileTypes.length == 0 || matchesFileType(psiFile, fileTypes))) {
              list.add(psiFile);
            }
          }
          return PsiUtilCore.toPsiFileArray(list);
        }
        else {
          return PsiFile.EMPTY_ARRAY;
        }
      }
    }
    return PsiFile.EMPTY_ARRAY;
  }

  private static boolean matchesFileType(PsiFile psiFile, FileType... fileTypes) {
    for (FileType fileType : fileTypes) {
      if (psiFile.getFileType().equals(fileType)) return true;
    }
    return false;
  }
}
