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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
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
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.structure.DomStructureViewBuilder;
import com.intellij.util.xml.stubs.FileStub;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {
  private static final Key<CachedValue<XmlFileHeader>> ROOT_TAG_NS_KEY = Key.create("rootTag&ns");
  private static final UserDataCache<CachedValue<XmlFileHeader>,XmlFile,Object> ourRootTagCache = new UserDataCache<CachedValue<XmlFileHeader>, XmlFile, Object>() {
    @Override
    protected CachedValue<XmlFileHeader> compute(final XmlFile file, final Object o) {
      return CachedValuesManager.getManager(file.getProject()).createCachedValue(
        () -> new CachedValueProvider.Result<>(calcXmlFileHeader(file), file), false);
    }
  };

  @NotNull
  private static XmlFileHeader calcXmlFileHeader(final XmlFile file) {

    if (file instanceof PsiFileEx && ((PsiFileEx)file).isContentsLoaded() && file.getNode().isParsed()) {
      return computeHeaderByPsi(file);
    }

    if (!XmlUtil.isStubBuilding() && file.getFileType() == XmlFileType.INSTANCE) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile instanceof VirtualFileWithId) {
        ObjectStubTree tree = StubTreeLoader.getInstance().readFromVFile(file.getProject(), virtualFile);
        if (tree != null) {
          Stub root = tree.getRoot();
          if (root instanceof FileStub) {
            return ((FileStub)root).getHeader();
          }
        }
      }
    }

    if (!file.isValid()) return XmlFileHeader.EMPTY;

    return NanoXmlUtil.parseHeader(file);
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

      String psiNs = tag.getNamespace();
      return new XmlFileHeader(localName, psiNs == XmlUtil.EMPTY_URI || Comparing.equal(psiNs, systemId) ? null : psiNs, publicId,
                               systemId);
    }
    return XmlFileHeader.EMPTY;
  }

  @Override
  public ModelMerger createModelMerger() {
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
      return ((DomFileElement)domElement).getFile();
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
  public XmlFileHeader getXmlFileHeader(XmlFile file) {
    return file.isValid() ? ourRootTagCache.get(ROOT_TAG_NS_KEY, file, null).getValue() : XmlFileHeader.EMPTY;
  }

  @Override
  public Collection<VirtualFile> getDomFileCandidates(Class<? extends DomElement> rootElementClass,
                                                      Project project,
                                                      final GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(DomFileIndex.NAME, rootElementClass.getName(), scope);
  }

  @Override
  public <T extends DomElement> List<DomFileElement<T>> getFileElements(final Class<T> clazz, final Project project, @Nullable final GlobalSearchScope scope) {
    final Collection<VirtualFile> list = getDomFileCandidates(clazz, project, scope != null ? scope : GlobalSearchScope.allScope(project));
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
  public StructureViewBuilder createSimpleStructureViewBuilder(final XmlFile file, final Function<DomElement, StructureViewMode> modeProvider) {
    return new DomStructureViewBuilder(file, modeProvider);
  }
}
