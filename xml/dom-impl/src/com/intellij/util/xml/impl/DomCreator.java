// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.ide.highlighter.DomSupportEnabled;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.xml.XmlTagDelegate;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.stubs.DomStub;
import com.intellij.util.xml.stubs.ElementStub;
import com.intellij.util.xml.stubs.FileStub;
import com.intellij.xml.util.IncludedXmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author peter
 */
final class DomCreator {

  @Nullable
  static DomInvocationHandler createTagHandler(@NotNull XmlTag tag) {
    PsiElement candidate = PhysicalDomParentStrategy.getParentTagCandidate(tag);
    if (!(candidate instanceof XmlTag)) {
      return createRootHandler(tag);
    }

    XmlTag parentTag = (XmlTag)candidate;

    DomInvocationHandler parent = getParentDom(parentTag);
    if (parent == null) return null;

    String localName = tag.getLocalName();
    if (StringUtil.isEmpty(localName)) return null;

    DomGenericInfoEx info = parent.getGenericInfo();
    DomFixedChildDescription fixedDescription = findChildrenDescription(info.getFixedChildrenDescriptions(), tag, parent);
    if (fixedDescription != null) {
      return createIndexedHandler(parent, localName, tag.getNamespace(), fixedDescription, tag);
    }
    DomCollectionChildDescription collectionDescription = findChildrenDescription(info.getCollectionChildrenDescriptions(), tag, parent);
    if (collectionDescription != null) {
      return createCollectionHandler(tag, collectionDescription, parent, parentTag);
    }

    return createCustomHandler(tag, parent, localName, info);
  }

  @Nullable
  private static DomInvocationHandler createRootHandler(XmlTag xmlTag) {
    PsiFile file = xmlTag.getContainingFile();
    DomFileElementImpl<?> element = file instanceof XmlFile ? DomManagerImpl.getDomManager(file.getProject()).getFileElement((XmlFile)file) : null;
    if (element != null) {
      final DomRootInvocationHandler handler = element.getRootHandler();
      if (handler.getXmlTag() == xmlTag) {
        return handler;
      }
    }
    return null;
  }

  @Nullable
  private static DomInvocationHandler createIndexedHandler(DomInvocationHandler parent,
                                                           String localName,
                                                           String namespace,
                                                           DomFixedChildDescription description, XmlTag tag) {
    final int totalCount = description.getCount();

    int index = 0;
    PsiElement current = tag;
    while (true) {
      current = current.getPrevSibling();
      if (current == null) {
        break;
      }
      if (current instanceof XmlTag) {
        final XmlTag xmlTag = (XmlTag)current;
        if (localName.equals(xmlTag.getLocalName()) && namespace.equals(xmlTag.getNamespace())) {
          index++;
          if (index >= totalCount) {
            return null;
          }
        }
      }
    }

    final DomManagerImpl myDomManager = parent.getManager();
    return new IndexedElementInvocationHandler(parent.createEvaluatedXmlName(description.getXmlName()), (FixedChildDescriptionImpl)description, index,
                                               new PhysicalDomParentStrategy(tag, myDomManager), myDomManager, null);
  }

  @NotNull
  private static CollectionElementInvocationHandler createCollectionHandler(XmlTag tag,
                                                                            DomCollectionChildDescription description,
                                                                            DomInvocationHandler parent,
                                                                            XmlTag parentTag) {
    DomStub parentStub = parent.getStub();
    if (parentStub != null) {
      int index = JBIterable
        .of(findSubTagsWithoutIncludes(parentTag, tag.getLocalName(), tag.getNamespace()))
        .filter(t -> !(t instanceof IncludedXmlTag))
        .indexOf(t -> t == tag);
      ElementStub stub = parentStub.getElementStub(tag.getName(), index);
      if (stub != null) {
        XmlName name = description.getXmlName();
        EvaluatedXmlNameImpl evaluatedXmlName = EvaluatedXmlNameImpl.createEvaluatedXmlName(name, name.getNamespaceKey(), true);
        return new CollectionElementInvocationHandler(evaluatedXmlName, (AbstractDomChildDescriptionImpl)description, parent.getManager(),
                                                      stub);
      }
    }
    return new CollectionElementInvocationHandler(description.getType(), tag, (AbstractCollectionChildDescription)description, parent,
                                                  null);
  }

  private static XmlTag @NotNull [] findSubTagsWithoutIncludes(@NotNull XmlTag parentTag,
                                                               @NlsSafe String localName,
                                                               @Nullable @NlsSafe String namespace) {
    return XmlTagDelegate.findSubTags(localName, namespace,
                                      parentTag instanceof XmlTagImpl ? ((XmlTagImpl)parentTag).getSubTags(false) : parentTag.getSubTags());
  }

  @Nullable
  private static DomInvocationHandler createCustomHandler(XmlTag tag,
                                                          DomInvocationHandler parent,
                                                          String localName,
                                                          DomGenericInfoEx info) {
    List<? extends CustomDomChildrenDescription> customs = info.getCustomNameChildrenDescription();
    if (customs.isEmpty()) return null;

    XmlFile file = parent.getFile();
    for (DomFixedChildDescription description : info.getFixedChildrenDescriptions()) {
      XmlName xmlName = description.getXmlName();
      if (localName.equals(xmlName.getLocalName()) && DomImplUtil.isNameSuitable(xmlName, tag, parent, file)) {
        return null;
      }
    }
    for (CustomDomChildrenDescription description : customs) {
      if (description.getTagNameDescriptor() != null) {
        AbstractCollectionChildDescription desc = (AbstractCollectionChildDescription)description;
        Type type = description.getType();
        return new CollectionElementInvocationHandler(type, tag, desc, parent, null);
      }
    }

    return null;
  }

  @Nullable
  static DomFileElementImpl<?> createFileElement(XmlFile xmlFile) {
    VirtualFile file = xmlFile.getVirtualFile();
    if (!(xmlFile.getFileType() instanceof DomSupportEnabled) || file != null && ProjectCoreUtil.isProjectOrWorkspaceFile(file)) {
      IdempotenceChecker.logTrace("DOM unsupported");
      return null;
    }

    DomFileDescription<?> description = findFileDescription(xmlFile);
    if (IdempotenceChecker.isLoggingEnabled()) {
      IdempotenceChecker.logTrace("DOM file description: " + description);
    }
    if (description == null) {
      return null;
    }

    XmlName xmlName = DomImplUtil.createXmlName(description.getRootTagName(), description.getRootElementClass(), null);
    assert xmlName != null;
    EvaluatedXmlNameImpl rootTagName1 = EvaluatedXmlNameImpl.createEvaluatedXmlName(xmlName, xmlName.getNamespaceKey(), false);

    FileStub stub = null;
    DomFileMetaData meta = DomApplicationComponent.getInstance().findMeta(description);
    if (meta != null && meta.hasStubs() && file instanceof VirtualFileWithId && !isFileParsed(xmlFile)) {
      if (FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() == null && !XmlUtil.isStubBuilding()) {
        ObjectStubTree<?> stubTree = StubTreeLoader.getInstance().readFromVFile(xmlFile.getProject(), file);
        if (stubTree != null) {
          stub = (FileStub)stubTree.getRoot();
        }
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    DomFileElementImpl<?> element = new DomFileElementImpl(xmlFile, rootTagName1, description, stub);
    xmlFile.putUserData(DomManagerImpl.CACHED_FILE_ELEMENT, new WeakReference<>(element));
    return element;
  }

  private static boolean isFileParsed(XmlFile myXmlFile) {
    return myXmlFile instanceof PsiFileEx && ((PsiFileEx)myXmlFile).isContentsLoaded();
  }

  @Nullable
  private static DomFileDescription<?> findFileDescription(XmlFile file) {
    DomFileDescription<?> mockDescription = file.getUserData(DomManagerImpl.MOCK_DESCRIPTION);
    if (mockDescription != null) return mockDescription;

    Project project = file.getProject();
    XmlFile originalFile = (XmlFile)file.getOriginalFile();
    DomManagerImpl domManager = DomManagerImpl.getDomManager(project);
    if (!originalFile.equals(file)) {
      DomFileElementImpl<?> element = domManager.getFileElement(originalFile);
      if (IdempotenceChecker.isLoggingEnabled()) {
        IdempotenceChecker.logTrace("Copy DOM from original file: " + element);
      }
      return element == null ? null : element.getFileDescription();
    }

    Module module = ModuleUtilCore.findModuleForFile(file);
    Condition<DomFileDescription<?>> condition = d -> d.isMyFile(file, module);
    String rootTagLocalName = DomService.getInstance().getXmlFileHeader(file).getRootTagLocalName();
    DomFileDescription<?> description = ContainerUtil.find(domManager.getFileDescriptions(rootTagLocalName), condition);
    return description != null ? description : ContainerUtil.find(domManager.getAcceptingOtherRootTagNameDescriptions(), condition);
  }

  @Nullable
  static DomInvocationHandler getParentDom(@NotNull XmlTag tag) {
    LinkedHashSet<XmlTag> allParents = new LinkedHashSet<>();
    PsiElement each = tag;
    while (each instanceof XmlTag && allParents.add((XmlTag)each)) {
      each = PhysicalDomParentStrategy.getParentTagCandidate((XmlTag)each);
    }
    ArrayList<XmlTag> list = new ArrayList<>(allParents);
    Collections.reverse(list);
    DomManagerImpl manager = DomManagerImpl.getDomManager(tag.getProject());
    for (XmlTag xmlTag : list) {
      manager.getDomHandler(xmlTag);
    }

    return manager.getDomHandler(tag);
  }

  @Nullable
  private static <T extends DomChildrenDescription> T findChildrenDescription(List<T> descriptions, XmlTag tag, DomInvocationHandler parent) {
    final XmlFile file = parent.getFile();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = descriptions.size(); i < size; i++) {
      T description = descriptions.get(i);
      if (DomImplUtil.isNameSuitable(description.getXmlName(), tag, parent, file)) {
        return description;
      }
    }
    return null;
  }

  @Nullable
  static AttributeChildInvocationHandler createAttributeHandler(@NotNull XmlAttribute attribute) {
    XmlTag tag = PhysicalDomParentStrategy.getParentTag(attribute);
    DomInvocationHandler handler = tag == null ? null : getParentDom(tag);
    if (handler == null) return null;

    String localName = attribute.getLocalName();
    Ref<AttributeChildInvocationHandler> result = Ref.create(null);
    handler.getGenericInfo().processAttributeChildrenDescriptions(description -> {
      if (description.getXmlName().getLocalName().equals(localName)) {
        final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(description.getXmlName());

        final String ns = evaluatedXmlName.getNamespace(tag, handler.getFile());
        //see XmlTagImpl.getAttribute(localName, namespace)
        if (ns.equals(tag.getNamespace()) && localName.equals(attribute.getName()) ||
            ns.equals(attribute.getNamespace())) {
          DomManagerImpl manager = handler.getManager();
          result.set(new AttributeChildInvocationHandler(evaluatedXmlName, description, manager,
                                                         new PhysicalDomParentStrategy(attribute, manager), null));
          return false;
        }
      }
      return true;
    });

    return result.get();
  }
}
