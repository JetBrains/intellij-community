/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.semantic.SemElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
@SuppressWarnings({"HardCodedStringLiteral", "StringConcatenationInsideStringBufferAppend"})
class FileDescriptionCachedValueProvider<T extends DomElement> implements SemElement{
  private static final Key<CachedValue<XmlFileHeader>> ROOT_TAG_NS_KEY = Key.create("rootTag&ns");
  private static final UserDataCache<CachedValue<XmlFileHeader>,XmlFile,Object> ourRootTagCache = new UserDataCache<CachedValue<XmlFileHeader>, XmlFile, Object>() {
    protected CachedValue<XmlFileHeader> compute(final XmlFile file, final Object o) {
      return CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<XmlFileHeader>() {
        public Result<XmlFileHeader> compute() {
          return new Result<XmlFileHeader>(DomImplUtil.getXmlFileHeader(file), file);
        }
      }, false);
    }
  };

  private final XmlFile myXmlFile;
  private volatile boolean myComputed;
  private volatile DomFileElementImpl<T> myLastResult;
  private final MyCondition myCondition = new MyCondition();

  private final DomManagerImpl myDomManager;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
  }

  @Nullable
  public final DomFileElementImpl<T> getFileElement() {
    if (myComputed) return myLastResult;

    _computeFileElement(false, getRootTag(), null);
    myXmlFile.putUserData(DomManagerImpl.CACHED_FILE_ELEMENT, myLastResult);
    myComputed = true;
    return myLastResult;
  }

  private void _computeFileElement(final boolean fireEvents, final XmlFileHeader rootTagName, @Nullable StringBuilder sb) {
    if (sb != null) {
      sb.append(rootTagName).append("\n");
    }

    if (!myXmlFile.isValid()) {
      myLastResult = null;
      return;
    }
    if (sb != null) {
      sb.append("File is valid\n");
    }

    final DomFileDescription<T> description = findFileDescription(rootTagName, sb);

    final DomFileElementImpl oldValue = getLastValue();
    if (sb != null) {
      sb.append("last " + oldValue + "\n");
    }
    final List<DomEvent> events = fireEvents ? new SmartList<DomEvent>() : Collections.<DomEvent>emptyList();
    if (oldValue != null) {
      if (fireEvents) {
        events.add(new ElementUndefinedEvent(oldValue));
      }
    }

    if (description == null) {
      myLastResult = null;
      return;
    }

    final Class<T> rootElementClass = description.getRootElementClass();
    final XmlName xmlName = DomImplUtil.createXmlName(description.getRootTagName(), rootElementClass, null);
    assert xmlName != null;
    final EvaluatedXmlNameImpl rootTagName1 = EvaluatedXmlNameImpl.createEvaluatedXmlName(xmlName, xmlName.getNamespaceKey(), false);
    myLastResult = new DomFileElementImpl<T>(myXmlFile, rootElementClass, rootTagName1, myDomManager, description);
    if (sb != null) {
      sb.append("success " + myLastResult + "\n");
    }

    if (fireEvents) {
      events.add(new ElementDefinedEvent(myLastResult));
    }
  }

  @Nullable
  private DomFileDescription<T> findFileDescription(final XmlFileHeader rootTagName, @Nullable StringBuilder sb) {
    final DomFileDescription<T> mockDescription = myXmlFile.getUserData(DomManagerImpl.MOCK_DESCIPRTION);
    if (mockDescription != null) return mockDescription;

    if (sb != null) {
      sb.append("no mock\n");
    }

    final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
    if (sb != null) {
      sb.append("original: " + originalFile + "\n");
    }
    if (originalFile != myXmlFile) {
      final FileDescriptionCachedValueProvider<T> provider = myDomManager.getOrCreateCachedValueProvider(originalFile);
      final DomFileElementImpl<T> element = provider.getFileElement();
      if (sb != null) {
        sb.append("originalDom " + element + "\n");
      }
      return element == null ? null : element.getFileDescription();
    }

    //noinspection unchecked
    final Set<DomFileDescription> namedDescriptions = myDomManager.getFileDescriptions(rootTagName.getRootTagLocalName());
    if (sb != null) {
      sb.append("named " + new HashSet<DomFileDescription>(namedDescriptions) + "\n");
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
  XmlFileHeader getRootTag() {
    return myXmlFile.isValid() ? ourRootTagCache.get(ROOT_TAG_NS_KEY, myXmlFile, null).getValue() : XmlFileHeader.EMPTY;
  }

  @Nullable
  final DomFileElementImpl<T> getLastValue() {
    return myLastResult;
  }

  public String getFileElementWithLogging() {
    final XmlFileHeader rootTagName = getRootTag();
    final StringBuilder log = new StringBuilder();
    _computeFileElement(false, rootTagName, log);
    return log.toString();
  }

  private class MyCondition implements Condition<DomFileDescription> {
    public Module module;

    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile, module);
    }
  }

}
