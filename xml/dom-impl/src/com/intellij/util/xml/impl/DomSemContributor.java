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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.semantic.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collection;

import static com.intellij.patterns.XmlPatterns.*;

/**
 * @author peter
 */
public class DomSemContributor extends SemContributor {
  private final SemService mySemService;

  public DomSemContributor(SemService semService) {
    mySemService = semService;
  }

  public void registerSemProviders(SemRegistrar registrar) {
    registrar.registerSemElementProvider(DomManagerImpl.FILE_DESCRIPTION_KEY, xmlFile(), new NullableFunction<XmlFile, FileDescriptionCachedValueProvider>() {
      public FileDescriptionCachedValueProvider fun(XmlFile xmlFile) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        return new FileDescriptionCachedValueProvider(DomManagerImpl.getDomManager(xmlFile.getProject()), xmlFile);
      }
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_HANDLER_KEY, xmlTag().withParent(psiElement(XmlElementType.XML_DOCUMENT).withParent(xmlFile())), new NullableFunction<XmlTag, DomInvocationHandler>() {
      public DomInvocationHandler fun(XmlTag xmlTag) {
        final FileDescriptionCachedValueProvider provider =
          mySemService.getSemElement(DomManagerImpl.FILE_DESCRIPTION_KEY, xmlTag.getContainingFile());
        assert provider != null;
        final DomFileElementImpl element = provider.getFileElement();
        if (element != null) {
          final DomRootInvocationHandler handler = element.getRootHandler();
          if (handler.getXmlTag() == xmlTag) {
            xmlTag.putUserData(DomManagerImpl.CACHED_DOM_HANDLER, handler);
            return handler;
          }
        }
        return null;
      }
    });

    final ElementPattern<XmlTag> nonRootTag = xmlTag().withParent(or(xmlTag(), xmlEntityRef().withParent(xmlTag())));
    registrar.registerSemElementProvider(DomManagerImpl.DOM_INDEXED_HANDLER_KEY, nonRootTag, new NullableFunction<XmlTag, IndexedElementInvocationHandler>() {
      public IndexedElementInvocationHandler fun(XmlTag tag) {
        final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
        assert parentTag != null;
        DomInvocationHandler parent = mySemService.getSemElement(DomManagerImpl.DOM_HANDLER_KEY, parentTag);
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
              if (localName.equals(xmlTag.getName()) && namespace.equals(xmlTag.getNamespace())) {
                index++;
                if (index >= totalCount) {
                  return null;
                }
              }
            }
          }

          final DomManagerImpl myDomManager = parent.getManager();
          final IndexedElementInvocationHandler handler =
            new IndexedElementInvocationHandler(parent.createEvaluatedXmlName(description.getXmlName()), (FixedChildDescriptionImpl)description, index,
                                                new PhysicalDomParentStrategy(tag, myDomManager), myDomManager, namespace);
          tag.putUserData(DomManagerImpl.CACHED_DOM_HANDLER, handler);
          return handler;
        }
        return null;
      }
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_COLLECTION_HANDLER_KEY, nonRootTag, new NullableFunction<XmlTag, CollectionElementInvocationHandler>() {
      public CollectionElementInvocationHandler fun(XmlTag tag) {
        final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
        assert parentTag != null;
        DomInvocationHandler parent = mySemService.getSemElement(DomManagerImpl.DOM_HANDLER_KEY, parentTag);
        if (parent == null) return null;

        final DomCollectionChildDescription description = findChildrenDescription(parent.getGenericInfo().getCollectionChildrenDescriptions(), tag, parent);
        if (description != null) {
          final CollectionElementInvocationHandler handler =
            new CollectionElementInvocationHandler(description.getType(), tag, (AbstractCollectionChildDescription)description, parent);
          tag.putUserData(DomManagerImpl.CACHED_DOM_HANDLER, handler);
          return handler;
        }
        return null;
      }
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_CUSTOM_HANDLER_KEY, nonRootTag, new NullableFunction<XmlTag, CollectionElementInvocationHandler>() {
      private final RecursionGuard myGuard = RecursionManager.createGuard("customDomParent");

      public CollectionElementInvocationHandler fun(XmlTag tag) {
        if (StringUtil.isEmpty(tag.getName())) return null;

        final XmlTag parentTag = PhysicalDomParentStrategy.getParentTag(tag);
        assert parentTag != null;

        DomInvocationHandler parent = myGuard.doPreventingRecursion(tag, new NullableComputable<DomInvocationHandler>() {
          @Override
          public DomInvocationHandler compute() {
            return mySemService.getSemElement(DomManagerImpl.DOM_HANDLER_KEY, parentTag);
          }
        });
        if (parent == null) return null;

        DomGenericInfoEx info = parent.getGenericInfo();
        final CustomDomChildrenDescription customDescription = info.getCustomNameChildrenDescription();
        if (customDescription == null) return null;

        if (mySemService.getSemElement(DomManagerImpl.DOM_INDEXED_HANDLER_KEY, tag) == null &&
            mySemService.getSemElement(DomManagerImpl.DOM_COLLECTION_HANDLER_KEY, tag) == null) {

          String localName = tag.getLocalName();
          XmlFile file = parent.getFile();
          for (final DomFixedChildDescription description : info.getFixedChildrenDescriptions()) {
            XmlName xmlName = description.getXmlName();
            if (localName.equals(xmlName.getLocalName()) && DomImplUtil.isNameSuitable(xmlName, tag, parent, file)) {
              return null;
            }
          }

          AbstractCollectionChildDescription desc = (AbstractCollectionChildDescription)customDescription;
          Type type = customDescription.getType();
          final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tag, desc, parent);
          tag.putUserData(DomManagerImpl.CACHED_DOM_HANDLER, handler);
          return handler;
        }

        return null;
      }
    });

    registrar.registerSemElementProvider(DomManagerImpl.DOM_ATTRIBUTE_HANDLER_KEY, xmlAttribute(), new NullableFunction<XmlAttribute, AttributeChildInvocationHandler>() {
      public AttributeChildInvocationHandler fun(final XmlAttribute attribute) {
        final XmlTag tag = PhysicalDomParentStrategy.getParentTag(attribute);
        final DomInvocationHandler handler = mySemService.getSemElement(DomManagerImpl.DOM_HANDLER_KEY, tag);
        if (handler == null) return null;

        final String localName = attribute.getLocalName();
        final Ref<AttributeChildInvocationHandler> result = Ref.create(null);
        handler.getGenericInfo().processAttributeChildrenDescriptions(new Processor<AttributeChildDescriptionImpl>() {
          public boolean process(AttributeChildDescriptionImpl description) {
            if (description.getXmlName().getLocalName().equals(localName)) {
              final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(description.getXmlName());

              final String ns = evaluatedXmlName.getNamespace(tag, handler.getFile());
              //see XmlTagImpl.getAttribute(localName, namespace)
              if (ns.equals(tag.getNamespace()) && localName.equals(attribute.getName()) ||
                  ns.equals(attribute.getNamespace())) {
                final DomManagerImpl myDomManager = handler.getManager();
                final AttributeChildInvocationHandler attributeHandler =
                  new AttributeChildInvocationHandler(evaluatedXmlName, description, myDomManager,
                                                      new PhysicalDomParentStrategy(attribute, myDomManager));
                attribute.putUserData(DomManagerImpl.CACHED_DOM_HANDLER, attributeHandler);
                result.set(attributeHandler);
                return false;
              }
            }
            return true;
          }
        });

        return result.get();
      }
    });

  }

  @Nullable
  private static <T extends DomChildrenDescription> T findChildrenDescription(Collection<T> descriptions, XmlTag tag, DomInvocationHandler parent) {
    final String localName = tag.getLocalName();
    final String namespace = tag.getNamespace();
    final String qName = tag.getName();

    final XmlFile file = parent.getFile();

    for (final T description : descriptions) {
      final XmlName xmlName = description.getXmlName();

      if (localName.equals(xmlName.getLocalName()) || qName.equals(xmlName.getLocalName())) {
        final EvaluatedXmlName evaluatedXmlName = parent.createEvaluatedXmlName(xmlName);
        if (DomImplUtil.isNameSuitable(evaluatedXmlName, localName, qName, namespace, file)) {
          return description;
        }
      }
    }
    return null;
  }
}
