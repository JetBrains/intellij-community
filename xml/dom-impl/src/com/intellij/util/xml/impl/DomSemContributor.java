// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.semantic.SemContributor;
import com.intellij.semantic.SemRegistrar;
import com.intellij.semantic.SemService;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.EvaluatedXmlNameImpl;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.stubs.DomStub;
import com.intellij.util.xml.stubs.ElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static com.intellij.patterns.XmlPatterns.*;

/**
 * @author peter
 */
final class DomSemContributor extends SemContributor {
  @Override
  public void registerSemProviders(@NotNull SemRegistrar registrar, @NotNull Project project) {
    registrar.registerSemElementProvider(DomManagerImpl.FILE_DESCRIPTION_KEY, xmlFile(), xmlFile -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      return new FileDescriptionCachedValueProvider(DomManagerImpl.getDomManager(xmlFile.getProject()), xmlFile);
    });

    final SemService semService = SemService.getSemService(project);
    registrar.registerSemElementProvider(DomManagerImpl.DOM_HANDLER_KEY, xmlTag().withParent(psiElement(XmlElementType.XML_DOCUMENT).withParent(xmlFile())),
                                         xmlTag -> {
                                           final FileDescriptionCachedValueProvider provider =
                                             semService.getSemElement(DomManagerImpl.FILE_DESCRIPTION_KEY, xmlTag.getContainingFile());
                                           assert provider != null;
                                           final DomFileElementImpl element = provider.getFileElement();
                                           if (element != null) {
                                             final DomRootInvocationHandler handler = element.getRootHandler();
                                             if (handler.getXmlTag() == xmlTag) {
                                               return handler;
                                             }
                                           }
                                           return null;
                                         });

    final ElementPattern<XmlTag> nonRootTag = xmlTag().withParent(or(xmlTag(), xmlEntityRef().withParent(xmlTag())));
    registrar.registerSemElementProvider(DomManagerImpl.DOM_INDEXED_HANDLER_KEY, nonRootTag, tag -> {
      final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
      assert parentTag != null;
      DomInvocationHandler parent = getParentDom(parentTag);
      if (parent == null) return null;

      final String localName = tag.getLocalName();
      final String namespace = tag.getNamespace();

      final DomFixedChildDescription description =
        findChildrenDescription(parent.getGenericInfo().getFixedChildrenDescriptions(), tag, parent);

      if (description != null) {

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
      return null;
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_COLLECTION_HANDLER_KEY, nonRootTag, tag -> {
      final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
      assert parentTag != null;
      DomInvocationHandler parent = getParentDom(parentTag);
      if (parent == null) return null;

      final DomCollectionChildDescription description = findChildrenDescription(parent.getGenericInfo().getCollectionChildrenDescriptions(), tag, parent);
      if (description != null) {
        DomStub parentStub = parent.getStub();
        if (parentStub != null) {
          int index = ArrayUtil.indexOf(parentTag.findSubTags(tag.getName(), tag.getNamespace()), tag);
          ElementStub stub = parentStub.getElementStub(tag.getLocalName(), index);
          if (stub != null) {
            XmlName name = description.getXmlName();
            EvaluatedXmlNameImpl evaluatedXmlName = EvaluatedXmlNameImpl.createEvaluatedXmlName(name, name.getNamespaceKey(), true);
            return new CollectionElementInvocationHandler(evaluatedXmlName, (AbstractDomChildDescriptionImpl)description, parent.getManager(), stub);
          }
        }
        return new CollectionElementInvocationHandler(description.getType(), tag, (AbstractCollectionChildDescription)description, parent, null);
      }
      return null;
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_CUSTOM_HANDLER_KEY, nonRootTag, new NullableFunction<XmlTag, CollectionElementInvocationHandler>() {
      private final RecursionGuard myGuard = RecursionManager.createGuard("customDomParent");

      @Override
      public CollectionElementInvocationHandler fun(XmlTag tag) {
        if (StringUtil.isEmpty(tag.getName())) return null;

        final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
        assert parentTag != null;

        DomInvocationHandler parent = myGuard.doPreventingRecursion(tag, true,
                                                                    (NullableComputable<DomInvocationHandler>)() -> getParentDom(parentTag));
        if (parent == null) return null;

        DomGenericInfoEx info = parent.getGenericInfo();
        final List<? extends CustomDomChildrenDescription> customs = info.getCustomNameChildrenDescription();
        if (customs.isEmpty()) return null;

        if (semService.getSemElement(DomManagerImpl.DOM_INDEXED_HANDLER_KEY, tag) == null &&
            semService.getSemElement(DomManagerImpl.DOM_COLLECTION_HANDLER_KEY, tag) == null) {

          String localName = tag.getLocalName();
          XmlFile file = parent.getFile();
          for (final DomFixedChildDescription description : info.getFixedChildrenDescriptions()) {
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
        }

        return null;
      }
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_ATTRIBUTE_HANDLER_KEY,
                                         xmlAttribute(),
                                         DomSemContributor::createAttributeHandler);
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
    final String localName = tag.getLocalName();
    String namespace = null;
    final String qName = tag.getName();

    final XmlFile file = parent.getFile();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = descriptions.size(); i < size; i++) {
      final T description = descriptions.get(i);
      final XmlName xmlName = description.getXmlName();

      if (localName.equals(xmlName.getLocalName()) || qName.equals(xmlName.getLocalName())) {
        final EvaluatedXmlName evaluatedXmlName = parent.createEvaluatedXmlName(xmlName);
        if (DomImplUtil.isNameSuitable(evaluatedXmlName,
                                       localName,
                                       qName,
                                       namespace == null ? namespace = tag.getNamespace() : namespace,
                                       file)) {
          return description;
        }
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
