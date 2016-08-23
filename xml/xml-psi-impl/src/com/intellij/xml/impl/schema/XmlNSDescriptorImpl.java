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
package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlNSDescriptorEx;
import com.intellij.xml.impl.ExternalDocumentValidator;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class XmlNSDescriptorImpl implements XmlNSDescriptorEx,Validator<XmlDocument>, DumbAware, XmlNSTypeDescriptorProvider {
  @NonNls
  public static final String XSD_PREFIX = "xsd";
  @NonNls public static final String SCHEMA_TAG_NAME = "schema";
  @NonNls public static final String IMPORT_TAG_NAME = "import";
  @NonNls static final String ELEMENT_TAG_NAME = "element";
  @NonNls static final String ATTRIBUTE_TAG_NAME = "attribute";
  @NonNls static final String COMPLEX_TYPE_TAG_NAME = "complexType";
  @NonNls static final String SEQUENCE_TAG_NAME = "sequence";
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.impl.schema.XmlNSDescriptorImpl");
  @NonNls private static final Set<String> STD_TYPES = new HashSet<>();
  private static final Set<String> UNDECLARED_STD_TYPES = new HashSet<>();
  @NonNls private static final String INCLUDE_TAG_NAME = "include";
  @NonNls private static final String REDEFINE_TAG_NAME = "redefine";
  private static final ThreadLocal<Set<PsiFile>> myRedefinedDescriptorsInProcessing = new ThreadLocal<>();
  private final Map<QNameKey, CachedValue<XmlElementDescriptor>> myDescriptorsMap = Collections.synchronizedMap(new HashMap<QNameKey, CachedValue<XmlElementDescriptor>>());
  private final Map<Pair<QNameKey, XmlTag>, CachedValue<TypeDescriptor>> myTypesMap = Collections.synchronizedMap(new HashMap<Pair<QNameKey,XmlTag>, CachedValue<TypeDescriptor>>());
  private XmlFile myFile;
  private XmlTag myTag;
  private String myTargetNamespace;
  private volatile Object[] dependencies;
  private MultiMap<String,XmlTag> mySubstitutions;

  public XmlNSDescriptorImpl(XmlFile file) {
    init(file.getDocument());
  }

  public XmlNSDescriptorImpl() {
  }

  private static void collectDependencies(@Nullable XmlTag myTag, @NotNull XmlFile myFile, @NotNull Set<PsiFile> visited) {
    if (visited.contains(myFile)) return;
    visited.add( myFile );

    if (myTag == null) return;
    XmlTag[] tags = myTag.getSubTags();

    for (final XmlTag tag : tags) {
      if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
          equalsToSchemaName(tag, IMPORT_TAG_NAME)
        ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(myFile, schemaLocation);
          addDependency(xmlFile, visited);
        }
      } else if (equalsToSchemaName(tag, REDEFINE_TAG_NAME)) {
        myRedefinedDescriptorsInProcessing.set(visited);
        try {
          final XmlFile file = getRedefinedElementDescriptorFile(tag);
          addDependency(file, visited);
        } finally {
          myRedefinedDescriptorsInProcessing.set(null);
        }
      }
    }

    final String schemaLocationDeclaration = myTag.getAttributeValue("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
    if(schemaLocationDeclaration != null) {
      final StringTokenizer tokenizer = new StringTokenizer(schemaLocationDeclaration);

      while(tokenizer.hasMoreTokens()){
        final String uri = tokenizer.nextToken();

        if(tokenizer.hasMoreTokens()){
          PsiFile resourceLocation = ExternalResourceManager.getInstance().getResourceLocation(tokenizer.nextToken(), myFile, null);
          if (resourceLocation == null) resourceLocation = ExternalResourceManager.getInstance().getResourceLocation(uri, myFile, null);

          if (resourceLocation instanceof XmlFile) addDependency((XmlFile)resourceLocation, visited);
        }
      }
    }
  }

  private static void addDependency(final XmlFile file, final Set<PsiFile> visited) {
    if (file != null) {
      final XmlDocument document = file.getDocument();
      collectDependencies(document != null ? document.getRootTag():null, file, visited);
    }
  }

  private static boolean checkSchemaNamespace(String name, XmlTag context){
    final String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(name));
    if(namespace.length() > 0){
      return checkSchemaNamespace(namespace);
    }
    return XSD_PREFIX.equals(XmlUtil.findPrefixByQualifiedName(name));
  }

  public static boolean checkSchemaNamespace(String namespace) {
    return XmlUtil.XML_SCHEMA_URI.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI2.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI3.equals(namespace);
  }

  public static boolean checkSchemaNamespace(@NotNull XmlTag context) {
    LOG.assertTrue(context.isValid());
    final String namespace = context.getNamespace();
    if (namespace.length() > 0) {
      return checkSchemaNamespace(namespace);
    }
    return StringUtil.startsWithConcatenation(context.getName(), XSD_PREFIX, ":");
  }

  static @NotNull XmlNSDescriptorImpl getNSDescriptorToSearchIn(XmlTag rootTag, final String name, XmlNSDescriptorImpl defaultNSDescriptor) {
    if (name == null) return defaultNSDescriptor;
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);

    if (namespacePrefix.length() > 0) {
      final String namespace = rootTag.getNamespaceByPrefix(namespacePrefix);
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(namespace, true);

      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        return (XmlNSDescriptorImpl)nsDescriptor;
      }
    }

    return defaultNSDescriptor;
  }

  @Nullable
  private static XmlElementDescriptor getDescriptorFromParent(final XmlTag tag, XmlElementDescriptor elementDescriptor) {
    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();
      if (descriptor != null) elementDescriptor = descriptor.getElementDescriptor(tag, (XmlTag)parent);
    }
    return elementDescriptor;
  }

  public static boolean processTagsInNamespace(@NotNull final XmlTag rootTag, String[] tagNames, PsiElementProcessor<XmlTag> processor) {
    return processTagsInNamespaceInner(rootTag, tagNames, processor, null);
  }

  private static boolean processTagsInNamespaceInner(@NotNull final XmlTag rootTag, final String[] tagNames,
                                                     final PsiElementProcessor<XmlTag> processor, Set<XmlTag> visitedTags) {
    if (visitedTags == null) visitedTags = new HashSet<>(3);
    else if (visitedTags.contains(rootTag)) return true;

    visitedTags.add(rootTag);
    XmlTag[] tags = rootTag.getSubTags();

    NextTag:
    for (XmlTag tag : tags) {
      for(String tagName:tagNames) {
        if (equalsToSchemaName(tag, tagName)) {
          final String name = tag.getAttributeValue("name");

          if (name != null) {
            if (!processor.execute(tag)) {
              return false;
            }
          }

          continue NextTag;
        }
      }

      if (equalsToSchemaName(tag, INCLUDE_TAG_NAME)) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument includedDocument = xmlFile.getDocument();

            if (includedDocument != null) {
              if (!processTagsInNamespaceInner(includedDocument.getRootTag(), tagNames, processor, visitedTags)) return false;
            }
          }
        }
      }
    }

    return true;
  }

  public static boolean equalsToSchemaName(@NotNull XmlTag tag, @NonNls String schemaName) {
    return schemaName.equals(tag.getLocalName()) && checkSchemaNamespace(tag);
  }

  private static @Nullable XmlTag findSpecialTag(@NonNls String name, @NonNls String specialName, XmlTag rootTag, XmlNSDescriptorImpl descriptor,
                                       HashSet<XmlTag> visited) {
    XmlNSDescriptorImpl nsDescriptor = getNSDescriptorToSearchIn(rootTag, name, descriptor);

    if (nsDescriptor != descriptor) {
      final XmlDocument document = nsDescriptor.getDescriptorFile() != null ? nsDescriptor.getDescriptorFile().getDocument():null;
      if (document == null) return null;

      return findSpecialTag(
        XmlUtil.findLocalNameByQualifiedName(name),
        specialName,
        document.getRootTag(),
        nsDescriptor,
        visited
      );
    }

    if (visited == null) visited = new HashSet<>(1);
    else if (visited.contains(rootTag)) return null;
    visited.add(rootTag);

    XmlTag[] tags = rootTag.getSubTags();

    return findSpecialTagIn(tags, specialName, name, rootTag, descriptor, visited);
  }

  private static XmlTag findSpecialTagIn(final XmlTag[] tags,
                                         final String specialName,
                                         final String name,
                                         final XmlTag rootTag,
                                         final XmlNSDescriptorImpl descriptor, final HashSet<XmlTag> visited) {
    for (XmlTag tag : tags) {
      if (equalsToSchemaName(tag, specialName)) {
        String attribute = tag.getAttributeValue("name");

        if (name.equals(attribute)
            || name.contains(":") && name.substring(name.indexOf(":") + 1).equals(attribute)) {
          return tag;
        }
      } else if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
                 ( equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
                   rootTag.getNamespaceByPrefix(
                     XmlUtil.findPrefixByQualifiedName(name)
                   ).equals(tag.getAttributeValue("namespace"))
                 )
         ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();
            if (document != null) {
              final XmlTag rTag = findSpecialTag(name, specialName, document.getRootTag(), descriptor, visited);

              if (rTag != null) return rTag;
            }
          }
        }
      } else if (equalsToSchemaName(tag, REDEFINE_TAG_NAME)) {
        XmlTag rTag = findSpecialTagIn(tag.getSubTags(), specialName, name, rootTag, descriptor, visited);
        if (rTag != null) return rTag;

        final XmlNSDescriptorImpl nsDescriptor = getRedefinedElementDescriptor(tag);
        if (nsDescriptor != null) {
          final XmlTag redefinedRootTag = ((XmlDocument)nsDescriptor.getDeclaration()).getRootTag();

          rTag = findSpecialTagIn(redefinedRootTag.getSubTags(), specialName, name, redefinedRootTag, nsDescriptor, visited);
          if (rTag != null) return rTag;
        }
      }
    }

    return null;
  }

  public static XmlNSDescriptorImpl getRedefinedElementDescriptor(final XmlTag parentTag) {
    XmlFile file = getRedefinedElementDescriptorFile(parentTag);
    if (file != null) {
      final XmlDocument document = file.getDocument();
      final PsiMetaData metaData = document != null ? document.getMetaData():null;
      if (metaData instanceof XmlNSDescriptorImpl) return (XmlNSDescriptorImpl)metaData;
    }
    return null;
  }

  public static XmlFile getRedefinedElementDescriptorFile(final XmlTag parentTag) {
    final String schemaL = parentTag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT);

    if (schemaL != null) {
      final PsiReference[] references = parentTag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, null).getValueElement().getReferences();

      if (references.length > 0) {
        final PsiElement psiElement = references[references.length - 1].resolve();

        if (psiElement instanceof XmlFile) {
          return ((XmlFile)psiElement);
        }
      }
    }
    return null;
  }

  @Override
  public XmlFile getDescriptorFile() {
    return myFile;
  }

  public String getDefaultNamespace(){
    return myTargetNamespace != null ? myTargetNamespace : "";
  }

  @Override
  @Nullable
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    return getElementDescriptor(localName, namespace, new HashSet<>(), false);
  }

  @Nullable
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace, Set<XmlNSDescriptorImpl> visited, boolean reference) {
    if(visited.contains(this)) return null;

    final QNameKey pair = new QNameKey(namespace, localName);
    final CachedValue<XmlElementDescriptor> descriptor = myDescriptorsMap.get(pair);
    if(descriptor != null) {
      final XmlElementDescriptor value = descriptor.getValue();
      if (value == null || value.getDeclaration().isValid()) return value;
    }

    final XmlTag rootTag = myTag;
    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();
    visited.add( this );

    LOG.assertTrue(rootTag.isValid());
    for (final XmlTag tag : tags) {
      if (equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if (checkElementNameEquivalence(localName, namespace, name, tag)) {
            final CachedValue<XmlElementDescriptor> cachedValue = CachedValuesManager.getManager(tag.getProject()).createCachedValue(() -> {
              final String name1 = tag.getAttributeValue("name");

              if (name1 != null && !name1.equals(pair.second)) {
                myDescriptorsMap.remove(pair);
                return new CachedValueProvider.Result<>(null, PsiModificationTracker.MODIFICATION_COUNT);
              }
              final XmlElementDescriptor xmlElementDescriptor = createElementDescriptor(tag);
              return new CachedValueProvider.Result<>(xmlElementDescriptor, xmlElementDescriptor.getDependences());
            }, false);
            myDescriptorsMap.put(pair, cachedValue);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
               (reference &&
                equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
                ( namespace.equals(tag.getAttributeValue("namespace")) ||
                  namespace.length() == 0 && tag.getAttributeValue("namespace") == null
                )
               )
        ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(rootTag.getContainingFile(), schemaLocation);
          if (xmlFile != null) {
            final XmlDocument includedDocument = xmlFile.getDocument();
            if (includedDocument != null) {
              final PsiMetaData data = includedDocument.getMetaData();
              if (data instanceof XmlNSDescriptorImpl) {
                final XmlElementDescriptor elementDescriptor =
                  ((XmlNSDescriptorImpl)data).getElementDescriptor(localName, namespace, visited, reference);
                if (elementDescriptor != null) {
                  //final CachedValue<XmlElementDescriptor> value = includedDocument.getManager().getCachedValuesManager()
                  //  .createCachedValue(new CachedValueProvider<XmlElementDescriptor>() {
                  //    public Result<XmlElementDescriptor> compute() {
                  //      return new Result<XmlElementDescriptor>(elementDescriptor, elementDescriptor.getDependences());
                  //    }
                  //  }, false);
                  //return value.getValue();
                  return elementDescriptor;
                }
              }
            }
          }
        }
      } else if (equalsToSchemaName(tag, REDEFINE_TAG_NAME)) {
        final XmlNSDescriptorImpl nsDescriptor = getRedefinedElementDescriptor(tag);
        if (nsDescriptor != null) {
          final XmlElementDescriptor xmlElementDescriptor = nsDescriptor.getElementDescriptor(localName, namespace, visited, reference);
          if (xmlElementDescriptor instanceof XmlElementDescriptorImpl) {
            return new RedefinedElementDescriptor((XmlElementDescriptorImpl)xmlElementDescriptor, this);
          }
        }
      }
    }

    return null;
  }

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    return new XmlElementDescriptorImpl(tag);
  }

  private boolean checkElementNameEquivalence(String localName, String namespace, String fqn, XmlTag context){
    final String localAttrName = XmlUtil.findLocalNameByQualifiedName(fqn);
    if (!localAttrName.equals(localName)) return false;
    final String attrNamespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(fqn));
    if (attrNamespace.equals(namespace)) return true;

    if(myTargetNamespace == null){
      if(XmlUtil.EMPTY_URI.equals(attrNamespace))
        return true;
    }
    else {
      if (myTargetNamespace.equals(namespace)) return true;
      return context.getNSDescriptor(namespace, true) == this; // schema's targetNamespace could be different from file systemId
    }
    return false;
  }

  @Nullable
  public XmlAttributeDescriptor getAttribute(String localName, String namespace, final XmlTag context) {
    return getAttributeImpl(localName, namespace, null);
  }

  @Nullable
  private XmlAttributeDescriptor getAttributeImpl(String localName, String namespace, @Nullable Set<XmlTag> visited) {
    if (myTag == null) return null;

    XmlNSDescriptor nsDescriptor = myTag.getNSDescriptor(namespace, true);

    if (nsDescriptor != this && nsDescriptor instanceof XmlNSDescriptorImpl) {
      return ((XmlNSDescriptorImpl)nsDescriptor).getAttributeImpl(
        localName,
        namespace,
        visited
      );
    }

    if (visited == null) visited = new HashSet<>(1);
    else if(visited.contains(myTag)) return null;
    visited.add(myTag);
    XmlTag[] tags = myTag.getSubTags();

    for (XmlTag tag : tags) {
      if (equalsToSchemaName(tag, ATTRIBUTE_TAG_NAME)) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if (checkElementNameEquivalence(localName, namespace, name, tag)) {
            return createAttributeDescriptor(tag);
          }
        }
      } else if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
                 (equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
                  namespace.equals(tag.getAttributeValue("namespace"))
                 )
        ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(myTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {

            final XmlDocument includedDocument = xmlFile.getDocument();
            if (includedDocument != null) {
              final PsiMetaData data = includedDocument.getMetaData();

              if(data instanceof XmlNSDescriptorImpl){
                final XmlAttributeDescriptor attributeDescriptor = ((XmlNSDescriptorImpl)data).getAttributeImpl(localName, namespace,visited);

                if(attributeDescriptor != null){
                  final CachedValue<XmlAttributeDescriptor> value = CachedValuesManager.getManager(includedDocument.getProject()).createCachedValue(
                    () -> {
                      Object[] deps = attributeDescriptor.getDependences();
                      if (deps.length == 0) {
                        LOG.error(attributeDescriptor + " (" + attributeDescriptor.getClass() + ") returned no dependencies");
                      }
                      return new CachedValueProvider.Result<>(attributeDescriptor, deps);
                    },
                    false
                  );
                  return value.getValue();
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  protected XmlAttributeDescriptorImpl createAttributeDescriptor(final XmlTag tag) {
    return new XmlAttributeDescriptorImpl(tag);
  }

  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    String type = descriptorTag.getAttributeValue("type");

    if (type != null) {
      return getTypeDescriptor(type, descriptorTag);
    }

    return findTypeDescriptorImpl(descriptorTag, null, null, null);
  }

  @Override
  public TypeDescriptor getTypeDescriptor(final String name, XmlTag context) {
    if(checkSchemaNamespace(name, context)){
      final String localNameByQualifiedName = XmlUtil.findLocalNameByQualifiedName(name);

      if (STD_TYPES.contains(localNameByQualifiedName) &&
          ( name.length() == localNameByQualifiedName.length() ||
            UNDECLARED_STD_TYPES.contains(localNameByQualifiedName)
          )
         )
        return new StdTypeDescriptor(localNameByQualifiedName);
    }

    return findTypeDescriptor(name, context);
  }

  @Nullable
  public XmlElementDescriptor getDescriptorByType(String qName, XmlTag instanceTag){
    if(myTag == null) return null;
    final TypeDescriptor typeDescriptor = findTypeDescriptor(qName, instanceTag);
    if(!(typeDescriptor instanceof ComplexTypeDescriptor)) return null;
    return new XmlElementDescriptorByType(instanceTag, (ComplexTypeDescriptor)typeDescriptor);
  }

  @Nullable
  protected TypeDescriptor findTypeDescriptor(final String qname) {
    return findTypeDescriptor(qname, myTag);
  }

  @Nullable
  protected TypeDescriptor findTypeDescriptor(final String qname, XmlTag context) {
    String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(qname));
    return findTypeDescriptor(XmlUtil.findLocalNameByQualifiedName(qname), namespace);
  }

  @Nullable
  private TypeDescriptor findTypeDescriptor(String localName, String namespace) {
    return findTypeDescriptorImpl(myTag, localName, namespace, null);
  }

  @Nullable
  protected TypeDescriptor findTypeDescriptorImpl(XmlTag rootTag, final String name, String namespace, Set<XmlTag> visited) {
    XmlNSDescriptorImpl responsibleDescriptor = this;
    if (namespace != null && namespace.length() != 0 && !namespace.equals(getDefaultNamespace())) {
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(namespace, true);

      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        responsibleDescriptor = (XmlNSDescriptorImpl)nsDescriptor;
      }
    }

    if (responsibleDescriptor != this) {
      return responsibleDescriptor.findTypeDescriptor(XmlUtil.findLocalNameByQualifiedName(name));
    }

    if (rootTag == null) return null;
    if (visited != null) {
      if (visited.contains(rootTag)) return null;
      visited.add(rootTag);
    }

    final Pair<QNameKey, XmlTag> pair = Pair.create(new QNameKey(name, namespace), rootTag);

    final CachedValue<TypeDescriptor> descriptor = myTypesMap.get(pair);
    if(descriptor != null) {
      TypeDescriptor value = descriptor.getValue();
      if (value == null ||
          ( value instanceof ComplexTypeDescriptor &&
            ((ComplexTypeDescriptor)value).getDeclaration().isValid()
          )
         )
      return value;
    }

    XmlTag[] tags = rootTag.getSubTags();

    if (visited == null) {
      visited = new HashSet<>(1);
      visited.add(rootTag);
    }

    return doFindIn(tags, name, namespace, pair, rootTag, visited);
  }

  private TypeDescriptor doFindIn(final XmlTag[] tags, final String name, final String namespace, final Pair<QNameKey, XmlTag> pair, final XmlTag rootTag, final Set<XmlTag> visited) {
    for (final XmlTag tag : tags) {
      if (equalsToSchemaName(tag, "complexType")) {
        if (name == null) {
          CachedValue<TypeDescriptor> value = createAndPutTypesCachedValue(tag, pair);
          return value.getValue();
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (isSameName(name, namespace, nameAttribute)) {
          CachedValue<TypeDescriptor> cachedValue = createAndPutTypesCachedValue(tag, pair);
          return cachedValue.getValue();
        }
      }
      else if (equalsToSchemaName(tag, "simpleType")) {

        if (name == null) {
          CachedValue<TypeDescriptor> value = createAndPutTypesCachedValueSimpleType(tag, pair);
          return value.getValue();
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (isSameName(name, namespace, nameAttribute)) {
          CachedValue<TypeDescriptor> cachedValue = createAndPutTypesCachedValue(tag, pair);
          return cachedValue.getValue();
        }
      }
      else if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
               ( equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
                 (namespace == null || !namespace.equals(getDefaultNamespace()))
               )
              ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findNamespace(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();

            if (document != null) {

              final CachedValue<TypeDescriptor> value = CachedValuesManager.getManager(tag.getProject()).createCachedValue(() -> {
                final String currentName = tag.getAttributeValue("name");

                if (( currentName != null &&
                      !currentName.equals(XmlUtil.findLocalNameByQualifiedName(name)) ) ||
                     !xmlFile.isValid() ||
                     xmlFile.getDocument() == null
                   ) {
                  myTypesMap.remove(pair);
                  return new CachedValueProvider.Result<>(null, PsiModificationTracker.MODIFICATION_COUNT);
                }

                final XmlDocument document1 = xmlFile.getDocument();
                final XmlNSDescriptorImpl nsDescriptor = findNSDescriptor(tag, document1);

                if (nsDescriptor == null) {
                  myTypesMap.remove(pair);
                  return new CachedValueProvider.Result<>(null, PsiModificationTracker.MODIFICATION_COUNT);
                }

                final XmlTag rTag = document1.getRootTag();

                final TypeDescriptor complexTypeDescriptor = nsDescriptor.findTypeDescriptorImpl(rTag, name, namespace, visited);
                return new CachedValueProvider.Result<>(complexTypeDescriptor, rTag);
              }, false
              );

              if (value.getValue() != null) {
                myTypesMap.put(pair, value);
                return value.getValue();
              }
            }
          }
        }
      } else if (equalsToSchemaName(tag, REDEFINE_TAG_NAME)) {
        final XmlTag[] subTags = tag.getSubTags();
        TypeDescriptor descriptor = doFindIn(subTags, name, namespace, pair, rootTag, visited);
        if (descriptor != null) return descriptor;

        final XmlNSDescriptorImpl nsDescriptor = getRedefinedElementDescriptor(tag);
        if (nsDescriptor != null) {
          final XmlTag redefinedRootTag = ((XmlDocument)nsDescriptor.getDeclaration()).getRootTag();
          descriptor = doFindIn(redefinedRootTag.getSubTags(), name, namespace, pair, redefinedRootTag, visited);
          if (descriptor != null) return descriptor;
        }
      }
    }
    return null;
  }

  private boolean isSameName(@NotNull String name, String namespace, String nameAttribute) {
    return nameAttribute != null &&
           (nameAttribute.equals(name) || (name.contains(":") && nameAttribute.equals(name.substring(name.indexOf(":") + 1)))) &&
           (namespace == null || namespace.length() == 0 || namespace.equals(getDefaultNamespace()))
      ;
  }

  private XmlNSDescriptorImpl findNSDescriptor(final XmlTag tag, final XmlDocument document) {
    final XmlNSDescriptorImpl nsDescriptor;
    if(IMPORT_TAG_NAME.equals(tag.getLocalName())) {
      final XmlNSDescriptor importedDescriptor = (XmlNSDescriptor)document.getMetaData();
      nsDescriptor = (importedDescriptor instanceof XmlNSDescriptorImpl) ?
                     (XmlNSDescriptorImpl)importedDescriptor:
                     this;
    }
    else {
      nsDescriptor = this;
    }
    return nsDescriptor;
  }

  private CachedValue<TypeDescriptor> createAndPutTypesCachedValueSimpleType(final XmlTag tag, final Pair<QNameKey, XmlTag> pair) {
    final CachedValue<TypeDescriptor> value = CachedValuesManager.getManager(tag.getProject()).createCachedValue(() -> {
      final SimpleTypeDescriptor simpleTypeDescriptor = new SimpleTypeDescriptor(tag);
      return new CachedValueProvider.Result<TypeDescriptor>(simpleTypeDescriptor, tag);
    }, false);
    myTypesMap.put(pair, value);
    return value;
  }

  private CachedValue<TypeDescriptor> createAndPutTypesCachedValue(final XmlTag tag, final Pair<QNameKey, XmlTag> pair) {
    final CachedValue<TypeDescriptor> value = CachedValuesManager.getManager(tag.getProject()).createCachedValue(
      () -> {
        final String name = tag.getAttributeValue("name");

        if (name != null &&
            pair.first != null &&
            pair.first.first != null &&
            !name.equals(XmlUtil.findLocalNameByQualifiedName(pair.first.first))
          ) {
          myTypesMap.remove(pair);
          return new CachedValueProvider.Result<>(null, PsiModificationTracker.MODIFICATION_COUNT);
        }
        final ComplexTypeDescriptor complexTypeDescriptor = new ComplexTypeDescriptor(this, tag);
        return new CachedValueProvider.Result<>(complexTypeDescriptor, tag);
      }, false);
    myTypesMap.put(pair, value);
    return value;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    PsiElement parent = tag.getParent();
    final String namespace = tag.getNamespace();
    while(parent instanceof XmlTag && !namespace.equals(((XmlTag)parent).getNamespace()))
      parent = parent.getContext();
    if (parent instanceof XmlTag) {
      final XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if(parentDescriptor != null){
        XmlElementDescriptor elementDescriptorFromParent = parentDescriptor.getElementDescriptor(tag, parentTag);

        if (elementDescriptorFromParent == null) {
          elementDescriptorFromParent = getDescriptorFromParent(tag, elementDescriptorFromParent);
        }
        if (elementDescriptorFromParent instanceof AnyXmlElementDescriptor) {
          final XmlElementDescriptor elementDescriptor = getElementDescriptor(tag.getLocalName(), namespace);
          if (elementDescriptor != null) return elementDescriptor;
        }
        return elementDescriptorFromParent;
      }
      else{
        return null;
      }
    }
    else {
      XmlElementDescriptor elementDescriptor = getElementDescriptor(tag.getLocalName(), tag.getNamespace());

      if (elementDescriptor == null) {
        elementDescriptor = getDescriptorFromParent(tag, elementDescriptor);
      }

      return elementDescriptor;
    }
  }

  @Override
  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument doc) {
    class CollectElementsProcessor implements PsiElementProcessor<XmlTag> {
      final List<XmlElementDescriptor> result = new ArrayList<>();

      @Override
      public boolean execute(@NotNull final XmlTag element) {
        ContainerUtil.addIfNotNull(result, getElementDescriptor(element.getAttributeValue("name"), getDefaultNamespace()));
        return true;
      }
    }

    CollectElementsProcessor processor = new CollectElementsProcessor() {
      @Override
      public boolean execute(@NotNull final XmlTag element) {
        if (!XmlElementDescriptorImpl.isAbstractDeclaration(element)) return super.execute(element);
        return true;
      }
    };
    processTagsInNamespace(myTag, new String[] {ELEMENT_TAG_NAME}, processor);

    return processor.result.toArray(new XmlElementDescriptor[processor.result.size()]);
  }

  public XmlAttributeDescriptor[] getRootAttributeDescriptors(final XmlTag context) {
    class CollectAttributesProcessor implements PsiElementProcessor<XmlTag> {
      final List<XmlAttributeDescriptor> result = new ArrayList<>();

      @Override
      public boolean execute(@NotNull final XmlTag element) {
        result.add(createAttributeDescriptor(element));
        return true;
      }
    }

    CollectAttributesProcessor processor = new CollectAttributesProcessor();
    processTagsInNamespace(myTag, new String[] {ATTRIBUTE_TAG_NAME}, processor);

    return processor.result.toArray(new XmlAttributeDescriptor[processor.result.size()]);
  }

  @Nullable
  public XmlTag findGroup(String name) {
    return findSpecialTag(name,"group",myTag, this, null);
  }

  @Nullable
  public XmlTag findAttributeGroup(String name) {
    return findSpecialTag(name, "attributeGroup", myTag, this, null);
  }

  public synchronized XmlElementDescriptor[] getSubstitutes(String localName, String namespace) {
    if (!initSubstitutes()) {
      return XmlElementDescriptor.EMPTY_ARRAY;
    }
    Collection<XmlTag> substitutions = mySubstitutions.get(localName);
    if (substitutions.isEmpty()) return XmlElementDescriptor.EMPTY_ARRAY;
    List<XmlElementDescriptor> result = new SmartList<>();
    for (XmlTag tag : substitutions) {
      final String substAttr = tag.getAttributeValue("substitutionGroup");
      if (substAttr != null && checkElementNameEquivalence(localName, namespace, substAttr, tag)) {
        result.add(createElementDescriptor(tag));
      }
    }

    return result.toArray(new XmlElementDescriptor[result.size()]);
  }

  private boolean initSubstitutes() {
    if (mySubstitutions == null && myTag != null) {
      mySubstitutions = new MultiMap<>();

      if (myTag == null) return false;

      XmlTag[] tags = myTag.getSubTags();

      for (XmlTag tag : tags) {
        if (equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
          final String substAttr = tag.getAttributeValue("substitutionGroup");
          if (substAttr != null) {
            String substLocalName = XmlUtil.findLocalNameByQualifiedName(substAttr);
            mySubstitutions.putValue(substLocalName, tag);
          }
        }
      }
    }
    return mySubstitutions != null;
  }

  @Override
  public PsiElement getDeclaration(){
    return myFile.getDocument();
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  @Override
  public String getName(){
    return "";
  }

  @Override
  public void init(PsiElement element){
    myFile = (XmlFile) element.getContainingFile();

    if (element instanceof XmlTag) {
      myTag = (XmlTag)element;
    } else {
      final XmlDocument document = myFile.getDocument();

      if (document != null) {
        myTag = document.getRootTag();
      }
    }

    if (myTag != null) {
      myTargetNamespace = myTag.getAttributeValue("targetNamespace");
    }

    final THashSet<PsiFile> dependenciesSet = new THashSet<>();
    final Set<PsiFile> redefineProcessingSet = myRedefinedDescriptorsInProcessing.get();
    if (redefineProcessingSet != null) {
      dependenciesSet.addAll(redefineProcessingSet);
    }
    collectDependencies(myTag, myFile, dependenciesSet);
    dependencies = ArrayUtil.toObjectArray(dependenciesSet);
  }

  @Override
  public Object[] getDependences() {
    if (dependencies == null) dependencies = myFile == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[] {myFile}; // init was not called
    return dependencies;
  }
  static {
    STD_TYPES.add("string");
    STD_TYPES.add("normalizedString");
    STD_TYPES.add("token");
    STD_TYPES.add("byte");
    STD_TYPES.add("unsignedByte");
    STD_TYPES.add("base64Binary");
    STD_TYPES.add("hexBinary");
    STD_TYPES.add("integer");
    STD_TYPES.add("positiveInteger");
    STD_TYPES.add("negativeInteger");
    STD_TYPES.add("nonNegativeInteger");
    STD_TYPES.add("nonPositiveInteger");
    STD_TYPES.add("int");
    STD_TYPES.add("unsignedInt");
    STD_TYPES.add("long");
    STD_TYPES.add("unsignedLong");
    STD_TYPES.add("short");
    STD_TYPES.add("unsignedShort");
    STD_TYPES.add("decimal");
    STD_TYPES.add("float");
    STD_TYPES.add("double");
    STD_TYPES.add("boolean");
    STD_TYPES.add("time");
    STD_TYPES.add("dateTime");
    STD_TYPES.add("duration");
    STD_TYPES.add("date");
    STD_TYPES.add("gMonth");
    STD_TYPES.add("gYear");
    STD_TYPES.add("gYearMonth");
    STD_TYPES.add("gDay");
    STD_TYPES.add("gMonthDay");
    STD_TYPES.add("Name");
    STD_TYPES.add("QName");
    STD_TYPES.add("NCName");
    STD_TYPES.add("anyURI");
    STD_TYPES.add("language");
    STD_TYPES.add("ID");
    STD_TYPES.add("IDREF");
    STD_TYPES.add("IDREFS");
    STD_TYPES.add("ENTITY");
    STD_TYPES.add("ENTITIES");
    STD_TYPES.add("NOTATION");
    STD_TYPES.add("NMTOKEN");
    STD_TYPES.add("NMTOKENS");
    STD_TYPES.add("anySimpleType");

    UNDECLARED_STD_TYPES.add("anySimpleType");
  }

  @Override
  public void validate(@NotNull XmlDocument context, @NotNull Validator.ValidationHost host) {
    ExternalDocumentValidator.doValidation(context,host);
  }

  public XmlTag getTag() {
    return myTag;
  }

  public synchronized boolean hasSubstitutions() {
    initSubstitutes();
    return mySubstitutions != null && mySubstitutions.size() > 0;
  }

  public boolean isValid() {
    return myFile != null && getDeclaration().isValid();
  }

  static class QNameKey extends Pair<String, String>{
     QNameKey(String name, String namespace) {
      super(name, namespace);
    }
  }
}
