// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs.builder;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.DomApplicationComponent;
import com.intellij.util.xml.impl.DomFileMetaData;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.stubs.FileStub;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * @author Dmitry Avdeev
 */
final class DomStubBuilder implements BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<DomFileMetaData> {
  private static final Logger LOG = Logger.getInstance(DomStubBuilder.class);

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return fileType == XmlFileType.INSTANCE && !FileBasedIndexImpl.isProjectOrWorkspaceFile(file, fileType);
  }


  @Override
  public @NotNull Stream<DomFileMetaData> getAllSubBuilders() {
    return DomApplicationComponent.getInstance().getStubBuildingMetadata().stream();
  }

  @Override
  public @Nullable DomFileMetaData getSubBuilder(@NotNull FileContent fileContent) {
    try {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.TRUE);
      PsiFile psiFile = fileContent.getPsiFile();
      if (!(psiFile instanceof XmlFile)) return null;

      Project project = fileContent.getProject();
      XmlFile xmlFile = (XmlFile)psiFile;
      DomFileElement<? extends DomElement> fileElement = DomManager.getDomManager(project).getFileElement(xmlFile);
      if (fileElement == null) return null;

      DomFileMetaData meta = DomApplicationComponent.getInstance().findMeta(fileElement.getFileDescription());
      if (meta == null || !meta.hasStubs()) return null;
      return meta;
    }
    finally {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.FALSE);
    }
  }

  @Override
  public @NotNull String getSubBuilderVersion(@Nullable DomFileMetaData data) {
    return data == null ? "<no-stub>" : data.rootTagName + ":" + data.rootTagName + ":" + data.implementation;
  }

  @Override
  public @Nullable Stub buildStubTree(@NotNull FileContent fileContent, @Nullable DomFileMetaData meta) {
    if (meta == null) return null;
    PsiFile psiFile = fileContent.getPsiFile();
    if (!(psiFile instanceof XmlFile)) return null;

    Project project = fileContent.getProject();
    XmlFile xmlFile = (XmlFile)psiFile;
    try {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.TRUE);
      DomFileElement<? extends DomElement> fileElement = DomManager.getDomManager(project).getFileElement(xmlFile);
      XmlFileHeader header = DomService.getInstance().getXmlFileHeader(xmlFile);
      if (header.getRootTagLocalName() == null) {
        LOG.error("null root tag for " + fileElement + " for " + fileContent.getFile());
      }
      FileStub fileStub = new FileStub(header);
      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null) {
        new DomStubBuilderVisitor(DomManagerImpl.getDomManager(project)).visitXmlElement(rootTag, fileStub, 0);
      }
      return fileStub;
    }
    finally {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.FALSE);
    }
  }

  @Override
  public int getStubVersion() {
    return 23;
  }
}
