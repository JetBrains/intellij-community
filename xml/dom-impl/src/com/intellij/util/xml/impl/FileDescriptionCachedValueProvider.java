/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.DomSupportEnabled;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.xml.XmlFile;
import com.intellij.semantic.SemElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.stubs.FileStub;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
@SuppressWarnings({"HardCodedStringLiteral", "StringConcatenationInsideStringBufferAppend"})
class FileDescriptionCachedValueProvider<T extends DomElement> implements SemElement{

  private final XmlFile myXmlFile;
  private volatile boolean myComputed;
  private volatile DomFileElementImpl<T> myLastResult;
  private final MyCondition myCondition = new MyCondition();

  private final DomManagerImpl myDomManager;
  private final DomService myDomService;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
    myDomService = DomService.getInstance();
  }

  @Nullable
  public final DomFileElementImpl<T> getFileElement() {
    if (myComputed) return myLastResult;

    DomFileElementImpl<T> result = _computeFileElement(false, myDomService.getXmlFileHeader(myXmlFile), null);

    synchronized (myCondition) {
      if (myComputed) return myLastResult;

      myLastResult = result;
      WeakReference<DomFileElementImpl> ref = result != null ? new WeakReference<>(result) : null;
      myXmlFile.putUserData(DomManagerImpl.CACHED_FILE_ELEMENT, ref);
      myComputed = true;
      return result;
    }
  }

  @Nullable
  private DomFileElementImpl<T> _computeFileElement(final boolean fireEvents,
                                                    @NotNull final XmlFileHeader rootTagName, @Nullable StringBuilder sb) {
    if (sb != null) {
      sb.append(rootTagName).append("\n");
    }

    if (!myXmlFile.isValid()) {
      return null;
    }
    if (sb != null) {
      sb.append("File is valid\n");
    }

    if (!(myXmlFile.getFileType() instanceof DomSupportEnabled)) {
      return null;
    }

    if (sb != null) {
      sb.append("File is of dom file type\n");
    }

    final DomFileDescription<T> description = findFileDescription(rootTagName, sb);

    final DomFileElementImpl oldValue = getLastValue();
    if (sb != null) {
      sb.append("last " + oldValue + "\n");
    }
    final List<DomEvent> events = fireEvents ? new SmartList<>() : Collections.<DomEvent>emptyList();
    if (oldValue != null) {
      if (fireEvents) {
        events.add(new DomEvent(oldValue, false));
      }
    }

    if (description == null) {
      return null;
    }

    final Class<T> rootElementClass = description.getRootElementClass();
    final XmlName xmlName = DomImplUtil.createXmlName(description.getRootTagName(), rootElementClass, null);
    assert xmlName != null;
    final EvaluatedXmlNameImpl rootTagName1 = EvaluatedXmlNameImpl.createEvaluatedXmlName(xmlName, xmlName.getNamespaceKey(), false);

    VirtualFile file = myXmlFile.getVirtualFile();
    FileStub stub = null;
    if (description.hasStubs() && file instanceof VirtualFileWithId && !isFileParsed()) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      if (!XmlUtil.isStubBuilding()) {
        ObjectStubTree stubTree = StubTreeLoader.getInstance().readOrBuild(myXmlFile.getProject(), file, myXmlFile);
        if (stubTree != null) {
          stub = (FileStub)stubTree.getRoot();
        }
      }
    }

    DomFileElementImpl<T> result = new DomFileElementImpl<>(myXmlFile, rootElementClass, rootTagName1, myDomManager, description, stub);
    if (sb != null) {
      sb.append("success " + result + "\n");
    }

    if (fireEvents) {
      events.add(new DomEvent(result, true));
    }
    return result;
  }

  private boolean isFileParsed() {
    return myXmlFile instanceof PsiFileEx && ((PsiFileEx)myXmlFile).isContentsLoaded();
  }

  @Nullable
  private DomFileDescription<T> findFileDescription(final XmlFileHeader xmlFileHeader, @Nullable StringBuilder sb) {
    final DomFileDescription<T> mockDescription = myXmlFile.getUserData(DomManagerImpl.MOCK_DESCRIPTION);
    if (mockDescription != null) return mockDescription;

    if (sb != null) {
      sb.append("no mock\n");
    }

    final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
    if (sb != null) {
      sb.append("original: " + originalFile + "\n");
    }
    if (!originalFile.equals(myXmlFile)) {
      final FileDescriptionCachedValueProvider<T> provider = myDomManager.getOrCreateCachedValueProvider(originalFile);
      final DomFileElementImpl<T> element = provider.getFileElement();
      if (sb != null) {
        sb.append("originalDom " + element + "\n");
      }
      return element == null ? null : element.getFileDescription();
    }

    //noinspection unchecked
    final Set<DomFileDescription> namedDescriptions = myDomManager.getFileDescriptions(xmlFileHeader.getRootTagLocalName());
    if (sb != null) {
      sb.append("named " + new HashSet<>(namedDescriptions) + "\n");
    }
    DomFileDescription<T> description = ContainerUtil.find(namedDescriptions, myCondition);
    if (description == null) {
      final Set<DomFileDescription> unnamed = myDomManager.getAcceptingOtherRootTagNameDescriptions();
      description = ContainerUtil.find(unnamed, myCondition);
    }
    if (sb != null) {
      sb.append("found " + description + "\n");
    }
    return description;
  }

  @Nullable
  final DomFileElementImpl<T> getLastValue() {
    return myLastResult;
  }

  public String getFileElementWithLogging() {
    final XmlFileHeader rootTagName = myDomService.getXmlFileHeader(myXmlFile);
    final StringBuilder log = new StringBuilder();
    myLastResult = _computeFileElement(false, rootTagName, log);
    return log.toString();
  }

  private class MyCondition implements Condition<DomFileDescription> {
    public Module module;

    @Override
    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile, module);
    }
  }

}
