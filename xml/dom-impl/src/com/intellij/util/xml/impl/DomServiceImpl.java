/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.util.xml.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.structure.DomStructureViewBuilder;
import com.intellij.util.xml.stubs.FileStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {

  @NotNull
  private static XmlFileHeader calcXmlFileHeader(@NotNull XmlFile file) {

    if (file instanceof PsiFileEx && ((PsiFileEx)file).isContentsLoaded() && file.getNode().isParsed()) {
      return computeHeaderByPsi(file);
    }

    if (FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() == null && file.getFileType() == XmlFileType.INSTANCE) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile instanceof VirtualFileWithId) {
        ObjectStubTree<?> tree = StubTreeLoader.getInstance().readFromVFile(file.getProject(), virtualFile);
        if (tree != null) {
          Stub root = tree.getRoot();
          if (root instanceof FileStub) {
            return ((FileStub)root).getHeader();
          }
        }
      }
    }

    if (!file.isValid()) return XmlFileHeader.EMPTY;

    XmlFileHeader header = NanoXmlUtil.parseHeader(file);
    if (header.getRootTagLocalName() == null) { // nanoxml failed
      return computeHeaderByPsi(file);
    }
    return header;
  }

  private static XmlFileHeader computeHeaderByPsi(XmlFile file) {
    final XmlDocument document = file.getDocument();
    if (document == null) {
      return XmlFileHeader.EMPTY;
    }

    String publicId = null;
    String systemId = null;
    final XmlProlog prolog = document.getProlog();
    if (prolog != null) {
      final XmlDoctype doctype = prolog.getDoctype();
      if (doctype != null) {
        publicId = doctype.getPublicId();
        systemId = doctype.getSystemId();
        if (systemId == null) {
          systemId = doctype.getDtdUri();
        }
      }
    }

    final XmlTag tag = document.getRootTag();
    if (tag == null) {
      return XmlFileHeader.EMPTY;
    }

    String localName = tag.getLocalName();
    if (StringUtil.isNotEmpty(localName)) {
      if (tag.getPrevSibling() instanceof PsiErrorElement) {
        return XmlFileHeader.EMPTY;
      }

      String psiNs = tag.getLocalNamespaceDeclarations().get(tag.getNamespacePrefix());
      return new XmlFileHeader(localName, psiNs, publicId, systemId);
    }
    return XmlFileHeader.EMPTY;
  }

  @Override
  public @NotNull ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  @Override
  public <T extends DomElement> DomAnchor<T> createAnchor(T domElement) {
    return DomAnchorImpl.createAnchor(domElement);
  }

  @Override
  @NotNull
  public XmlFile getContainingFile(@NotNull DomElement domElement) {
    if (domElement instanceof DomFileElement) {
      return ((DomFileElement<?>)domElement).getFile();
    }
    return DomManagerImpl.getNotNullHandler(domElement).getFile();
  }

  @Override
  @NotNull
  public EvaluatedXmlName getEvaluatedXmlName(@NotNull final DomElement element) {
    return DomManagerImpl.getNotNullHandler(element).getXmlName();
  }

  @Override
  @NotNull
  public XmlFileHeader getXmlFileHeader(@NotNull XmlFile file) {
    if (FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() != null) {
      return calcXmlFileHeader(file);
    }

    return CachedValuesManager.getCachedValue(file, () -> new CachedValueProvider.Result<>(calcXmlFileHeader(file), file));
  }

  @Override
  public @NotNull Collection<VirtualFile> getDomFileCandidates(@NotNull Class<? extends DomElement> rootElementClass,
                                                               @NotNull GlobalSearchScope scope) {
    DomFileDescription<?> description = DomApplicationComponent
      .getInstance()
      .findFileDescription(rootElementClass);
    if (description == null) return Collections.emptySet();

    String[] namespaces = description.getAllPossibleRootTagNamespaces();
    if (namespaces.length == 0) {
      namespaces = new String[]{null};
    }
    String rootTagName = description.getRootTagName();

    Set<VirtualFile> files = new HashSet<>();
    for (String namespace : namespaces) {
      files.addAll(DomFileIndex.findFiles(rootTagName, namespace, scope));
    }
    return files;
  }

  @Override
  public <T extends DomElement> @NotNull List<DomFileElement<T>> getFileElements(@NotNull Class<T> clazz, @NotNull Project project, @Nullable GlobalSearchScope scope) {
    final Collection<VirtualFile> list = getDomFileCandidates(clazz, scope != null ? scope : GlobalSearchScope.allScope(project));
    if (list.isEmpty()) return Collections.emptyList();

    final ArrayList<DomFileElement<T>> result = new ArrayList<>(list.size());
    for (VirtualFile file : list) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof XmlFile) {
        final DomFileElement<T> element = DomManager.getDomManager(project).getFileElement((XmlFile)psiFile, clazz);
        if (element != null) {
          result.add(element);
        }
      }
    }
    return result;
  }


  @Override
  public @NotNull StructureViewBuilder createSimpleStructureViewBuilder(@NotNull XmlFile file, @NotNull Function<DomElement, StructureViewMode> modeProvider) {
    return new DomStructureViewBuilder(file, modeProvider);
  }
}
