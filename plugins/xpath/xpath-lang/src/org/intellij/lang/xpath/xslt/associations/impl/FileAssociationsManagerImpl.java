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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsManagerImpl")
final class FileAssociationsManagerImpl extends FileAssociationsManager implements Disposable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(FileAssociationsManagerImpl.class);

  private final Project myProject;
  private final Map<VirtualFilePointer, VirtualFilePointerContainer> myAssociations;
  private boolean myTempCopy;

  FileAssociationsManagerImpl(Project project) {
    myProject = project;
    myAssociations = new LinkedHashMap<>();
  }

  public void markAsTempCopy() {
    myTempCopy = true;
  }

  @Override
  public void loadState(@NotNull Element state) {
    clear();
    final List<Element> children = state.getChildren("file");
    VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
    for (Element child : children) {
      final String url = child.getAttributeValue("url");
      if (url != null) {
        final VirtualFilePointer pointer = filePointerManager.create(url, this, null);
        final VirtualFilePointerContainer container = filePointerManager.createContainer(this);
        container.readExternal(child, "association", false);
        myAssociations.put(pointer, container);
      }
    }
  }

  @Override
  public void noStateLoaded() {
    clear();
  }

  @Override
  public @Nullable Element getState() {
    if (myAssociations.isEmpty()) return null;
    
    Element element = new Element("state");
    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      final Element e = new Element("file");
      e.setAttribute("url", pointer.getUrl());
      final VirtualFilePointerContainer container = myAssociations.get(pointer);
      container.writeExternal(e, "association", false);
      element.addContent(e);
    }
    return element;
  }

  public TransactionalManager getTempManager() {
    return new TempManager(this, myProject);
  }

  @Override
  public void dispose() {
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

  private HashMap<VirtualFilePointer, VirtualFilePointerContainer> copy(FileAssociationsManagerImpl other) {
    final HashMap<VirtualFilePointer, VirtualFilePointerContainer> hashMap = new LinkedHashMap<>();

    final Set<VirtualFilePointer> virtualFilePointers = other.myAssociations.keySet();
    VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
    for (VirtualFilePointer pointer : virtualFilePointers) {
      final VirtualFilePointerContainer container = filePointerManager.createContainer(this);
      container.addAll(other.myAssociations.get(pointer));
      hashMap.put(filePointerManager.duplicate(pointer, this, null), container);
    }
    return hashMap;
  }

  @Override
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

  @Override
  public void removeAssociation(PsiFile file, PsiFile assoc) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    if (assoc.getVirtualFile() == null) return;

    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.getUrl().equals(virtualFile.getUrl())) {
        VirtualFilePointerContainer container = myAssociations.get(pointer);
        if (container != null) {
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

  @Override
  public void addAssociation(PsiFile file, PsiFile assoc) {
    final VirtualFile virtualFile = assoc.getVirtualFile();
    if (virtualFile == null) {
      LOG.warn("No VirtualFile for " + file.getName());
      return;
    }
    addAssociation(file, virtualFile);
  }

  @Override
  public void addAssociation(PsiFile file, VirtualFile assoc) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      LOG.warn("No VirtualFile for " + file.getName());
      return;
    }

    VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
    for (VirtualFilePointer pointer : myAssociations.keySet()) {
      if (pointer.getUrl().equals(virtualFile.getUrl())) {
        VirtualFilePointerContainer container = myAssociations.get(pointer);
        if (container == null) {
          container = filePointerManager.createContainer(this);
          myAssociations.put(pointer, container);
        }
        if (container.findByUrl(assoc.getUrl()) == null) {
          container.add(assoc);
          touch();
        }
        return;
      }
    }
    final VirtualFilePointerContainer container = filePointerManager.createContainer(this);
    container.add(assoc);
    myAssociations.put(filePointerManager.create(virtualFile, this, null), container);
    touch();
  }

  @Override
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

  @Override
  public PsiFile[] getAssociationsFor(PsiFile file) {
    return getAssociationsFor(file, FileType.EMPTY_ARRAY);
  }

  @Override
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
