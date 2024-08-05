// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlNSDescriptorEx;
import com.intellij.xml.impl.ExternalDocumentValidator;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class XmlNSDescriptorImpl implements XmlNSDescriptorEx,Validator<XmlDocument>, DumbAware, XsdNsDescriptor {
  public static final @NonNls String XSD_PREFIX = "xsd";
  public static final @NonNls String SCHEMA_TAG_NAME = "schema";
  public static final @NonNls String IMPORT_TAG_NAME = "import";
  static final @NonNls String ELEMENT_TAG_NAME = "element";
  static final @NonNls String ATTRIBUTE_TAG_NAME = "attribute";
  static final @NonNls String COMPLEX_TYPE_TAG_NAME = "complexType";
  static final @NonNls String SEQUENCE_TAG_NAME = "sequence";
  private static final Logger LOG = Logger.getInstance(XmlNSDescriptorImpl.class);
  private static final @NonNls Set<String> STD_TYPES = new HashSet<>();
  private static final Set<String> UNDECLARED_STD_TYPES = new HashSet<>();
  private static final @NonNls String INCLUDE_TAG_NAME = "include";
  private static final @NonNls String REDEFINE_TAG_NAME = "redefine";
  private final Map<QNameKey, CachedValue<XmlElementDescriptor>> myDescriptorsMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<Pair<QNameKey, XmlTag>, CachedValue<TypeDescriptor>> myTypesMap = Collections.synchronizedMap(new HashMap<>());
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
        RecursionManager.doPreventingRecursion(tag, false, () -> {
          final XmlFile file = getRedefinedElementDescriptorFile(tag);
          addDependency(file, visited);
          return null;
        });
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

  @Override
  public @Nullable XmlElementDescriptor getElementDescriptor(String localName, String namespace, Set<? super XmlNSDescriptorImpl> visited, boolean reference) {
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
              return new CachedValueProvider.Result<>(xmlElementDescriptor, xmlElementDescriptor.getDependencies());
            }, false);
            myDescriptorsMap.put(pair, cachedValue);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, INCLUDE_TAG_NAME) ||
               (reference &&
                equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
                (namespace.equals(tag.getAttributeValue("namespace")) ||
                 namespace.isEmpty() && tag.getAttributeValue("namespace") == null
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
            return new RedefinedElementDescriptor((XmlElementDescriptorImpl)xmlElementDescriptor, this, nsDescriptor);
          }
        }
      }
    }

    return null;
  }

  public static boolean checkSchemaNamespace(String namespace) {
    return XmlUtil.XML_SCHEMA_URI.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI2.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI3.equals(namespace);
  }

  private @Nullable TypeDescriptor findTypeDescriptorImpl(@Nullable XmlTag rootTag, final String name, String namespace) {
    if (rootTag == null) return null;
    return RecursionManager.doPreventingRecursion(Trinity.create(rootTag, name, namespace), true, () -> {
      XmlNSDescriptorImpl responsibleDescriptor = this;
      if (namespace != null && !namespace.isEmpty() && !namespace.equals(getDefaultNamespace())) {
        final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(namespace, true);

        if (nsDescriptor instanceof XmlNSDescriptorImpl) {
          responsibleDescriptor = (XmlNSDescriptorImpl)nsDescriptor;
        }
      }

      if (responsibleDescriptor != this) {
        return responsibleDescriptor.findTypeDescriptor(name, namespace);
      }

      final Pair<QNameKey, XmlTag> pair = Pair.create(new QNameKey(name, namespace), rootTag);
      final CachedValue<TypeDescriptor> descriptor = myTypesMap.get(pair);
      if (descriptor != null) {
        TypeDescriptor value = descriptor.getValue();
        if (value == null ||
            (value instanceof ComplexTypeDescriptor &&
             ((ComplexTypeDescriptor)value).getDeclaration().isValid()
            )
          ) {
          return value;
        }
      }

      XmlTag[] tags = rootTag.getSubTags();
      return doFindIn(tags, name, namespace, pair, rootTag);
    });
  }

  private boolean isSameName(@NotNull String name, String namespace, String nameAttribute) {
    return nameAttribute != null &&
           (nameAttribute.equals(name) || (name.contains(":") && nameAttribute.equals(name.substring(name.indexOf(":") + 1)))) &&
           (namespace == null || namespace.isEmpty() || namespace.equals(getDefaultNamespace()))
      ;
  }

  private static @Nullable XmlElementDescriptor getDescriptorFromParent(final XmlTag tag, XmlElementDescriptor elementDescriptor) {
    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();
      if (descriptor != null) elementDescriptor = descriptor.getElementDescriptor(tag, (XmlTag)parent);
    }
    return elementDescriptor;
  }

  @Override
  public final boolean processTagsInNamespace(String[] tagNames, PsiElementProcessor<? super XmlTag> processor) {
    return processTagsInNamespaceInner(myTag, tagNames, processor, null);
  }

  private static boolean processTagsInNamespaceInner(final @NotNull XmlTag rootTag, final String[] tagNames,
                                                     final PsiElementProcessor<? super XmlTag> processor, Set<? super XmlTag> visitedTags) {
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

  private static XmlFile getRedefinedElementDescriptorFile(final XmlTag parentTag) {
    XmlAttribute attribute = parentTag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT);
    if (attribute != null) {
      XmlAttributeValue element = attribute.getValueElement();
      PsiElement psiElement = new URLReference(element).resolve();
        if (psiElement instanceof XmlFile) {
          return ((XmlFile)psiElement);
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
  public @Nullable XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    return getElementDescriptor(localName, namespace, new HashSet<>(), false);
  }

  private static boolean checkSchemaNamespace(@NotNull String name, @NotNull XmlTag context){
    final String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(name));
    if(!namespace.isEmpty()){
      return checkSchemaNamespace(namespace);
    }
    return XSD_PREFIX.equals(XmlUtil.findPrefixByQualifiedName(name));
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

  @Override
  public @Nullable XmlAttributeDescriptor getAttribute(String localName, String namespace, final XmlTag context) {
    return getAttributeImpl(localName, namespace, null);
  }

  private @Nullable XmlAttributeDescriptor getAttributeImpl(String localName, String namespace, @Nullable Set<XmlTag> visited) {
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
            return new XmlAttributeDescriptorImpl(tag);
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
                      Object[] deps = attributeDescriptor.getDependencies();
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

  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    String type = descriptorTag.getAttributeValue("type");

    if (type != null) {
      return getTypeDescriptor(type, descriptorTag);
    }

    return findTypeDescriptorImpl(descriptorTag, null, null);
  }

  @Override
  public TypeDescriptor getTypeDescriptor(@NotNull String name, XmlTag context) {
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

  public @Nullable XmlElementDescriptor getDescriptorByType(String qName, XmlTag instanceTag){
    if(myTag == null) return null;
    final TypeDescriptor typeDescriptor = findTypeDescriptor(qName, instanceTag);
    if(!(typeDescriptor instanceof ComplexTypeDescriptor)) return null;
    return new XmlElementDescriptorByType(instanceTag, (ComplexTypeDescriptor)typeDescriptor);
  }

  protected @Nullable TypeDescriptor findTypeDescriptor(final String qname) {
    return findTypeDescriptor(qname, myTag);
  }

  private @Nullable TypeDescriptor findTypeDescriptor(final String qname, @NotNull XmlTag context) {
    String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(qname));
    String localName = XmlUtil.findLocalNameByQualifiedName(qname);
    return findTypeDescriptorImpl(myTag, localName, namespace.isEmpty() ? getDefaultNamespace() : namespace);
  }

  @Override
  public @Nullable TypeDescriptor findTypeDescriptor(String localName, String namespace) {
    return findTypeDescriptorImpl(myTag, localName, namespace);
  }

  public static boolean checkSchemaNamespace(@NotNull XmlTag context) {
    LOG.assertTrue(context.isValid());
    final String namespace = context.getNamespace();
    if (!namespace.isEmpty()) {
      return checkSchemaNamespace(namespace);
    }
    return StringUtil.startsWithConcatenation(context.getName(), XSD_PREFIX, ":");
  }

  private TypeDescriptor doFindIn(final XmlTag[] tags,
                                  final String name,
                                  final String namespace,
                                  final Pair<QNameKey, XmlTag> pair,
                                  final XmlTag rootTag) {
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
               (equalsToSchemaName(tag, IMPORT_TAG_NAME) &&
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

                final TypeDescriptor complexTypeDescriptor = nsDescriptor.findTypeDescriptorImpl(rTag, name, namespace);
                return new CachedValueProvider.Result<>(complexTypeDescriptor, rTag);
              }, false
              );

              TypeDescriptor type = value.getValue();
              if (type != null) {
                myTypesMap.put(pair, value);
                return type;
              }
            }
          }
        }
      }
      else if (equalsToSchemaName(tag, REDEFINE_TAG_NAME)) {
        final XmlTag[] subTags = tag.getSubTags();
        TypeDescriptor descriptor = doFindIn(subTags, name, namespace, pair, rootTag);
        if (descriptor != null) return descriptor;

        final XmlNSDescriptorImpl nsDescriptor = getRedefinedElementDescriptor(tag);
        if (nsDescriptor != null) {
          final XmlTag redefinedRootTag = ((XmlDocument)nsDescriptor.getDeclaration()).getRootTag();
          descriptor = doFindIn(redefinedRootTag.getSubTags(), name, namespace, pair, redefinedRootTag);
          if (descriptor instanceof ComplexTypeDescriptor) {
            TypeDescriptor finalDescriptor = descriptor;
            CachedValue<TypeDescriptor> value = CachedValuesManager.getManager(tag.getProject()).createCachedValue(() -> {
              RedefinedTypeDescriptor typeDescriptor = new RedefinedTypeDescriptor((ComplexTypeDescriptor)finalDescriptor, this, nsDescriptor);
              return CachedValueProvider.Result.create(typeDescriptor, PsiModificationTracker.MODIFICATION_COUNT);
            });
            myTypesMap.put(pair, value);
            return value.getValue();
          }
          if (descriptor != null) {
            return descriptor;
          }
        }
      }
    }
    return null;
  }

  static @NotNull XmlNSDescriptorImpl getNSDescriptorToSearchIn(XmlTag rootTag, final String name, XmlNSDescriptorImpl defaultNSDescriptor) {
    if (name == null) return defaultNSDescriptor;
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);

    if (!namespacePrefix.isEmpty()) {
      final String namespace = rootTag.getNamespaceByPrefix(namespacePrefix);
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(namespace, true);

      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        return (XmlNSDescriptorImpl)nsDescriptor;
      }
    }

    return defaultNSDescriptor;
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
      return new CachedValueProvider.Result<>(simpleTypeDescriptor, tag);
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
    if (parent instanceof XmlTag parentTag) {
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if(parentDescriptor != null){
        XmlElementDescriptor elementDescriptorFromParent = parentDescriptor.getElementDescriptor(tag, parentTag);

        if (elementDescriptorFromParent == null) {
          elementDescriptorFromParent = getDescriptorFromParent(tag, null);
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
        elementDescriptor = getDescriptorFromParent(tag, null);
      }

      return elementDescriptor;
    }
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument doc) {
    class CollectElementsProcessor implements PsiElementProcessor<XmlTag> {
      final List<XmlElementDescriptor> result = new ArrayList<>();

      @Override
      public boolean execute(final @NotNull XmlTag element) {
        ContainerUtil.addIfNotNull(result, getElementDescriptor(element.getAttributeValue("name"), getDefaultNamespace()));
        return true;
      }
    }

    CollectElementsProcessor processor = new CollectElementsProcessor() {
      @Override
      public boolean execute(final @NotNull XmlTag element) {
        if (!XmlElementDescriptorImpl.isAbstractDeclaration(element)) return super.execute(element);
        return true;
      }
    };
    processTagsInNamespace(new String[] {ELEMENT_TAG_NAME}, processor);

    return processor.result.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  public XmlAttributeDescriptor[] getRootAttributeDescriptors(final XmlTag context) {
    if (myTag == null) return XmlAttributeDescriptor.EMPTY;
    return CachedValuesManager.getProjectPsiDependentCache(myTag, XmlNSDescriptorImpl::computeAttributeDescriptors)
      .toArray(XmlAttributeDescriptor.EMPTY);
  }

  private static List<XmlAttributeDescriptor> computeAttributeDescriptors(XmlTag tag) {
    List<XmlAttributeDescriptor> result = new ArrayList<>();
    processTagsInNamespaceInner(tag, new String[] {ATTRIBUTE_TAG_NAME},
                                element -> result.add(new XmlAttributeDescriptorImpl(element)),
                                null);
    return result;
  }

  @Override
  public @Nullable XmlTag findGroup(String name) {
    return findSpecialTag(name,"group",myTag, this, null);
  }

  @Override
  public @Nullable XmlTag findAttributeGroup(String name) {
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

    return result.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  private boolean initSubstitutes() {
    if (mySubstitutions == null && myTag != null) {
      mySubstitutions = new MultiMap<>();

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

    Set<PsiFile> dependenciesSet = new HashSet<>();
    collectDependencies(myTag, myFile, dependenciesSet);
    dependencies = ArrayUtil.toObjectArray(dependenciesSet);
  }

  @Override
  public Object @NotNull [] getDependencies() {
    if (dependencies == null) dependencies = myFile == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : new Object[] {myFile}; // init was not called
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
