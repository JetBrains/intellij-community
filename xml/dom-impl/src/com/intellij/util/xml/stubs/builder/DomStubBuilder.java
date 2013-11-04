/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs.builder;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.stubs.FileStub;
import com.intellij.xml.util.XmlUtil;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public class DomStubBuilder implements BinaryFileStubBuilder {

  public final static Key<FileContent> CONTENT_FOR_DOM_STUBS = Key.create("dom stubs content");
  private final static Logger LOG = Logger.getInstance(DomStubBuilder.class);

  @Override
  public boolean acceptsFile(VirtualFile file) {
    FileType fileType = file.getFileType();
    return fileType == XmlFileType.INSTANCE && !FileBasedIndexImpl.isProjectOrWorkspaceFile(file, fileType);
  }

  @Override
  public Stub buildStubTree(FileContent fileContent) {
    VirtualFile file = fileContent.getFile();
    Project project = fileContent.getProject();
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) return null;

    XmlFile xmlFile = (XmlFile)psiFile;
    try {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.TRUE);
      psiFile.putUserData(CONTENT_FOR_DOM_STUBS, fileContent);
      DomFileElement<? extends DomElement> fileElement = DomManager.getDomManager(project).getFileElement(xmlFile);
      if (fileElement == null || !fileElement.getFileDescription().hasStubs()) return null;

      XmlFileHeader header = DomService.getInstance().getXmlFileHeader(xmlFile);
      if (header.getRootTagLocalName() == null) {
        LOG.error("null root tag for " + fileElement + " for " + file);
      }
      FileStub fileStub = new FileStub(header);
      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null) {
        new DomStubBuilderVisitor(DomManagerImpl.getDomManager(project)).visitXmlElement(rootTag, fileStub);
      }
      return fileStub;
    }
    finally {
      XmlUtil.BUILDING_DOM_STUBS.set(Boolean.FALSE);
      psiFile.putUserData(CONTENT_FOR_DOM_STUBS, null);
    }
  }

  @Override
  public int getStubVersion() {
    int version = 9;
    DomFileDescription[] descriptions = Extensions.getExtensions(DomFileDescription.EP_NAME);
    for (DomFileDescription description : descriptions) {
      version += description.getStubVersion();
    }
    return version;
  }
}
