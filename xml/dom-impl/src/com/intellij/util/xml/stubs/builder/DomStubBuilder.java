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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import com.intellij.util.xml.stubs.FileStub;

import java.io.ByteArrayInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public class DomStubBuilder implements BinaryFileStubBuilder {

  public static boolean isStubBuilding(PsiFile file) {
    return Boolean.TRUE.equals(file.getUserData(BUILDING_DOM_STUBS));
  }
  private final static Key<Boolean> BUILDING_DOM_STUBS = Key.create("building dom stubs...");

  private final static Logger LOG = Logger.getInstance(DomStubBuilder.class);

  @Override
  public boolean acceptsFile(VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE;
  }

  @Override
  public Stub buildStubTree(VirtualFile file, byte[] content, Project project) {

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) return null;

    XmlFile xmlFile = (XmlFile)psiFile;
    DomManager manager = DomManager.getDomManager(project);
    try {
      xmlFile.putUserData(BUILDING_DOM_STUBS, Boolean.TRUE);
      DomFileElement<? extends DomElement> fileElement = manager.getFileElement(xmlFile);
      if (fileElement == null || !fileElement.getFileDescription().hasStubs()) return null;

      XmlFileHeader header = NanoXmlUtil.parseHeader(new ByteArrayInputStream(content));
      if (header.getRootTagLocalName() == null) {
        LOG.error("null root tag for " + fileElement + " for " + file);
      }
      FileStub fileStub = new FileStub(header);
      DomStubBuilderVisitor visitor = new DomStubBuilderVisitor(fileStub);
      visitor.visitDomElement(fileElement.getRootElement());
      return fileStub;
    }
    finally {
      xmlFile.putUserData(BUILDING_DOM_STUBS, null);
    }
  }

  @Override
  public int getStubVersion() {
    int version = 8;
    DomFileDescription[] descriptions = Extensions.getExtensions(DomFileDescription.EP_NAME);
    for (DomFileDescription description : descriptions) {
      version += description.getStubVersion();
    }
    return version;
  }
}
