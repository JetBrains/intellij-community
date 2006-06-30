package com.intellij.xml.impl.schema;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.ExternalDocumentValidator;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class XmlNSDescriptorImpl implements XmlNSDescriptor,Validator {
  @NonNls private static final Set<String> STD_TYPES = new HashSet<String>();
  private static final Set<String> UNDECLARED_STD_TYPES = new HashSet<String>();
  private XmlFile myFile;
  private XmlTag myTag;
  private String myTargetNamespace;
  @NonNls
  public static final String XSD_PREFIX = "xsd";
  @NonNls static final String ELEMENT_TAG_NAME = "element";

  public XmlNSDescriptorImpl(XmlFile file) {
    init(file.getDocument());
  }
                                          
  public XmlNSDescriptorImpl() {
  }

  public XmlFile getDescriptorFile() {
    return myFile;
  }

  public boolean isHierarhyEnabled() {
    return true;
  }

  public String getDefaultNamespace(){
    return myTargetNamespace != null ? myTargetNamespace : "";
  }

  private final Map<Pair<String, String>, CachedValue<XmlElementDescriptor>> myDescriptorsMap = new HashMap<Pair<String,String>, CachedValue<XmlElementDescriptor>>();
  private final Map<Pair<String, XmlTag>, CachedValue<TypeDescriptor>> myTypesMap = new HashMap<Pair<String,XmlTag>, CachedValue<TypeDescriptor>>();

  @Nullable
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    return getElementDescriptor(localName, namespace, new HashSet<XmlNSDescriptorImpl>(),false);
  }

  public XmlElementDescriptor getElementDescriptor(String localName, String namespace, Set<XmlNSDescriptorImpl> visited, boolean reference) {
    if(visited.contains(this)) return null;

    final Pair<String, String> pair = new Pair<String, String>(namespace, localName);
    final CachedValue<XmlElementDescriptor> descriptor = myDescriptorsMap.get(pair);
    if(descriptor != null) {
      final XmlElementDescriptor value = descriptor.getValue();
      if (value == null || value.getDeclaration().isValid()) return value;
    }

    final XmlTag rootTag = myTag;
    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();
    visited.add( this );

    for (final XmlTag tag : tags) {
      if (equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if (checkElementNameEquivalence(localName, namespace, name, tag)) {
            final CachedValue<XmlElementDescriptor> cachedValue =
              tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlElementDescriptor>() {
                public Result<XmlElementDescriptor> compute() {
                  final String name = tag.getAttributeValue("name");
                  
                  if (name != null && !name.equals(pair.second)) {
                    myDescriptorsMap.remove(pair);
                    return new Result<XmlElementDescriptor>(null);
                  }
                  final XmlElementDescriptor xmlElementDescriptor = createElementDescriptor(tag);
                  return new Result<XmlElementDescriptor>(xmlElementDescriptor, xmlElementDescriptor.getDependences());
                }
              }, false);
            myDescriptorsMap.put(pair, cachedValue);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, "include") ||
               (reference &&
                equalsToSchemaName(tag, "import") &&
                namespace.equals(tag.getAttributeValue("namespace"))
               )
        ) {
        final XmlAttribute schemaLocation = tag.getAttribute("schemaLocation", tag.getNamespace());
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation.getValue());
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
    if(myTargetNamespace == null){
      final String attrNamespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(fqn));
      if(attrNamespace.equals(namespace) || XmlUtil.EMPTY_URI.equals(attrNamespace))
        return true;
    }
    else {
      final boolean b = myTargetNamespace.equals(namespace);
      if (b) return b;
      return context.getNSDescriptor(namespace, true) == this; // schema's targetNamespace could be different from file systemId
    }
    return false;
  }

  public XmlAttributeDescriptorImpl getAttribute(String localName, String namespace) {
    return getAttributeImpl(localName, namespace,null);
  }

  private XmlAttributeDescriptorImpl getAttributeImpl(String localName, String namespace, Set<XmlTag> visited) {
    if (myTag == null) return null;

    XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)myTag.getNSDescriptor(namespace, true);

    if (nsDescriptor != this && nsDescriptor != null) {
      return nsDescriptor.getAttributeImpl(
        localName,
        namespace,
        visited
      );
    }

    if (visited == null) visited = new HashSet<XmlTag>(1);
    else if(visited.contains(myTag)) return null;
    visited.add(myTag);
    XmlTag[] tags = myTag.getSubTags();

    for (XmlTag tag : tags) {
      if (equalsToSchemaName(tag, "attribute")) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if (checkElementNameEquivalence(localName, namespace, name, tag)) {
            return createAttributeDescriptor(tag);
          }
        }
      } else if (equalsToSchemaName(tag, "include") ||
                 (equalsToSchemaName(tag, "import") &&
                  namespace.equals(tag.getAttributeValue("namespace"))
                 )
        ) {
        final XmlAttribute schemaLocation = tag.getAttribute("schemaLocation", tag.getNamespace());

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(myTag.getContainingFile(), schemaLocation.getValue());

          if (xmlFile != null) {

            final XmlDocument includedDocument = xmlFile.getDocument();
            if (includedDocument != null) {
              final PsiMetaData data = includedDocument.getMetaData();

              if(data instanceof XmlNSDescriptorImpl){
                final XmlAttributeDescriptorImpl attributeDescriptor = ((XmlNSDescriptorImpl)data).getAttributeImpl(localName, namespace,visited);

                if(attributeDescriptor != null){
                  final CachedValue<XmlAttributeDescriptorImpl> value = includedDocument.getManager().getCachedValuesManager().createCachedValue(
                    new CachedValueProvider<XmlAttributeDescriptorImpl>(){
                      public Result<XmlAttributeDescriptorImpl> compute() {
                        return new Result<XmlAttributeDescriptorImpl>(attributeDescriptor, attributeDescriptor.getDependences());
                      }
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

  protected TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    String type = descriptorTag.getAttributeValue("type");

    if (type != null) {
      return getTypeDescriptor(type, descriptorTag);
    }

    return findTypeDescriptor(descriptorTag, null);
  }

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

    return findTypeDescriptor(myTag, name);
  }

  public XmlElementDescriptor getDescriptorByType(String qName, XmlTag instanceTag){
    if(myTag == null) return null;
    final TypeDescriptor typeDescriptor = findTypeDescriptor(myTag, qName);
    if(!(typeDescriptor instanceof ComplexTypeDescriptor)) return null;
    return new XmlElementDescriptorByType(instanceTag, (ComplexTypeDescriptor)typeDescriptor);
  }

  private boolean checkSchemaNamespace(String name, XmlTag context){
    final String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(name));
    if(namespace != null && namespace.length() > 0){
      return checkSchemaNamespace(namespace);
    }
    return XSD_PREFIX.equals(XmlUtil.findPrefixByQualifiedName(name));
  }

  private static boolean checkSchemaNamespace(String namespace) {
    return XmlUtil.XML_SCHEMA_URI.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI2.equals(namespace) ||
           XmlUtil.XML_SCHEMA_URI3.equals(namespace);
  }

  private static boolean checkSchemaNamespace(XmlTag context){
    final String namespace = context.getNamespace();
    if(namespace != null && namespace.length() > 0){
      return checkSchemaNamespace(namespace);
    }
    return context.getName().startsWith(XSD_PREFIX + ":");
  }

  private static XmlNSDescriptorImpl getNSDescriptorToSearchIn(XmlTag rootTag, final String name, XmlNSDescriptorImpl defaultNSDescriptor) {
    if (name == null) return defaultNSDescriptor;
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);

    if (namespacePrefix != null && namespacePrefix.length() > 0) {
      final String namespace = rootTag.getNamespaceByPrefix(namespacePrefix);
      final XmlNSDescriptor nsDescriptor = rootTag.getNSDescriptor(namespace, true);

      if (nsDescriptor instanceof XmlNSDescriptorImpl) {
        return (XmlNSDescriptorImpl)nsDescriptor;
      }
    }

    return defaultNSDescriptor;
  }

  protected TypeDescriptor findTypeDescriptor(XmlTag rootTag, final String name) {
    return findTypeDescriptorImpl(rootTag, name, null);
  }

  protected TypeDescriptor findTypeDescriptorImpl(XmlTag rootTag, final String name, Set<XmlTag> visited) {
    XmlNSDescriptorImpl nsDescriptor = getNSDescriptorToSearchIn(rootTag, name, this);

    if (nsDescriptor != this) {
      return nsDescriptor.findTypeDescriptor(
        nsDescriptor.getDescriptorFile().getDocument().getRootTag(),
        XmlUtil.findLocalNameByQualifiedName(name)
      );
    }

    final Pair<String, XmlTag> pair = new Pair<String, XmlTag>(name, rootTag);
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

    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();

    if (visited == null) visited = new HashSet<XmlTag>(1);
    else if (visited.contains(rootTag)) return null;
    visited.add(rootTag);

    return doFindIn(tags, name, pair, rootTag, visited);
  }

  private TypeDescriptor doFindIn(final XmlTag[] tags, final String name, final Pair<String, XmlTag> pair, final XmlTag rootTag, final Set<XmlTag> visited) {
    XmlNSDescriptorImpl nsDescriptor;

    for (final XmlTag tag : tags) {
      if (equalsToSchemaName(tag, "complexType")) {
        if (name == null) {
          CachedValue<TypeDescriptor> value = createAndPutTypesCachedValue(tag, pair);
          return value.getValue();
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (nameAttribute != null) {
          if (nameAttribute.equals(name)
              || (name.indexOf(":") >= 0 && nameAttribute.equals(name.substring(name.indexOf(":") + 1)))
            ) {
            CachedValue<TypeDescriptor> cachedValue = createAndPutTypesCachedValue(tag, pair);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, "simpleType")) {

        if (name == null) {
          CachedValue<TypeDescriptor> value = createAndPutTypesCachedValueSimpleType(tag, pair);
          return value.getValue();
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (name.equals(nameAttribute)
            || name.indexOf(":") >= 0 && name.substring(name.indexOf(":") + 1).equals(nameAttribute)
          ) {
          CachedValue<TypeDescriptor> cachedValue = createAndPutTypesCachedValue(tag, pair);
          return cachedValue.getValue();
        }
      }
      else if (equalsToSchemaName(tag, "include") ||
               ( equalsToSchemaName(tag, "import") &&
                 rootTag.getNamespaceByPrefix(
                   XmlUtil.findPrefixByQualifiedName(name)
                 ).equals(tag.getAttributeValue("namespace"))
               )
              ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();

            if (document != null) {
              final XmlTag rTag = document.getRootTag();

              if("import".equals(tag.getLocalName())) {
                final XmlNSDescriptor importedDescriptor = (XmlNSDescriptor)document.getMetaData();
                nsDescriptor = (importedDescriptor instanceof XmlNSDescriptorImpl) ?
                               (XmlNSDescriptorImpl)importedDescriptor:
                               this;
              }
              else {
                nsDescriptor = this;
              }


              final Set<XmlTag> visited1 = visited;
              final XmlNSDescriptorImpl nsDescriptor1 = nsDescriptor;

              final CachedValue<TypeDescriptor> value =
                tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<TypeDescriptor>() {
                  public Result<TypeDescriptor> compute() {
                    final String currentName = tag.getAttributeValue("name");

                    if (currentName != null && !currentName.equals(XmlUtil.findLocalNameByQualifiedName(name))) {
                      myTypesMap.remove(pair);
                      return new Result<TypeDescriptor>(null);
                    }

                    final TypeDescriptor complexTypeDescriptor =
                      (nsDescriptor1 != XmlNSDescriptorImpl.this)?
                      nsDescriptor1.findTypeDescriptor(rTag, name):
                      nsDescriptor1.findTypeDescriptorImpl(rTag, name,visited1);
                    return new Result<TypeDescriptor>(complexTypeDescriptor, rTag);
                  }
                }, false
              );

              if (value.getValue() != null) {
                myTypesMap.put(pair, value);
                return value.getValue();
              }
            }
          }
        }
      } else if (equalsToSchemaName(tag, "redefine")) {
        final XmlTag[] subTags = tag.getSubTags();
        final TypeDescriptor descriptor = doFindIn(subTags, name, pair, rootTag, visited);
        if (descriptor != null) return descriptor;
      }
    }
    return null;
  }

  private CachedValue<TypeDescriptor> createAndPutTypesCachedValueSimpleType(final XmlTag tag, final Pair<String, XmlTag> pair) {
    final CachedValue<TypeDescriptor> value = tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<TypeDescriptor>() {
      public CachedValueProvider.Result<TypeDescriptor> compute() {
        final SimpleTypeDescriptor simpleTypeDescriptor = new SimpleTypeDescriptor(tag);
        return new Result<TypeDescriptor>(simpleTypeDescriptor, tag);
      }
    }, false);
    myTypesMap.put(pair, value);
    return value;
  }

  private CachedValue<TypeDescriptor> createAndPutTypesCachedValue(final XmlTag tag, final Pair<String, XmlTag> pair) {
    final CachedValue<TypeDescriptor> value = tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<TypeDescriptor>() {
      public CachedValueProvider.Result<TypeDescriptor> compute() {
        final String name = tag.getAttributeValue("name");
        
        if (name != null && pair.first != null && !name.equals(XmlUtil.findLocalNameByQualifiedName(pair.first))) {
          myTypesMap.remove(pair);
          return new Result<TypeDescriptor>(null);
        }
        final ComplexTypeDescriptor complexTypeDescriptor = new ComplexTypeDescriptor(XmlNSDescriptorImpl.this, tag); 
        return new Result<TypeDescriptor>(complexTypeDescriptor, tag);
      }
    }, false);
    myTypesMap.put(pair, value);
    return value;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    PsiElement parent = tag.getParent();
    final String namespace = tag.getNamespace();
    while(parent instanceof XmlTag && !namespace.equals(((XmlTag)parent).getNamespace()))
      parent = parent.getContext();
    if (parent instanceof XmlTag) {
      final XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if(parentDescriptor != null){
        XmlElementDescriptor elementDescriptorFromParent = parentDescriptor.getElementDescriptor(tag);

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

  private static XmlElementDescriptor getDescriptorFromParent(final XmlTag tag, XmlElementDescriptor elementDescriptor) {
    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();
      if (descriptor != null) elementDescriptor = descriptor.getElementDescriptor(tag);
    }
    return elementDescriptor;
  }

  public XmlElementDescriptor[] getRootElementsDescriptors(final XmlDocument doc) {
    class CollectElementsProcessor implements PsiElementProcessor<XmlTag> {
      final List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();
      
      public boolean execute(final XmlTag element) {
        result.add(getElementDescriptor(element.getAttributeValue("name"),getDefaultNamespace()));
        return true;
      }
    }
    
    CollectElementsProcessor processor = new CollectElementsProcessor();
    processTagsInNamespace(myTag, new String[] {ELEMENT_TAG_NAME}, processor);

    return processor.result.toArray(new XmlElementDescriptor[processor.result.size()]);
  }

  public boolean processTagsInNamespace(final XmlTag rootTag, String[] tagNames, PsiElementProcessor<XmlTag> processor) {
    return processTagsInNamespaceInner(rootTag, tagNames, processor, null);
  }

  private static boolean processTagsInNamespaceInner(final XmlTag rootTag, final String[] tagNames,
                                                     final PsiElementProcessor<XmlTag> processor, Set<XmlTag> visitedTags) {
    if (visitedTags == null) visitedTags = new HashSet<XmlTag>(3);
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

      if (equalsToSchemaName(tag, "include")) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation);

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

  protected static boolean equalsToSchemaName(XmlTag tag, @NonNls String schemaName) {
    return schemaName.equals(tag.getLocalName()) && checkSchemaNamespace(tag);
  }

  private static XmlTag findSpecialTag(@NonNls String name, @NonNls String specialName, XmlTag rootTag, XmlNSDescriptorImpl descriptor,
                                       HashSet<XmlTag> visited) {
    XmlNSDescriptorImpl nsDescriptor = getNSDescriptorToSearchIn(rootTag, name, descriptor);

    if (nsDescriptor != descriptor) {
      return findSpecialTag(
        XmlUtil.findLocalNameByQualifiedName(name),
        specialName,
        nsDescriptor.getDescriptorFile().getDocument().getRootTag(),
        nsDescriptor,
        visited
      );
    }

    if (visited == null) visited = new HashSet<XmlTag>(1);
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
            || name.indexOf(":") >= 0 && name.substring(name.indexOf(":") + 1).equals(attribute)) {
          return tag;
        }
      } else if (equalsToSchemaName(tag,"include") ||
                 ( equalsToSchemaName(tag, "import") &&
                   rootTag.getNamespaceByPrefix(
                     XmlUtil.findPrefixByQualifiedName(name)
                   ).equals(tag.getAttributeValue("namespace"))
                 )
         ) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();
            if (document != null) {
              final XmlTag rTag = findSpecialTag(name, specialName, document.getRootTag(), descriptor, visited);

              if (rTag != null) return rTag;
            }
          }
        }
      } else if (equalsToSchemaName(tag, "redefine")) {
        final XmlTag rTag = findSpecialTagIn(tag.getSubTags(), specialName, name, rootTag, descriptor, visited);
        if (rTag != null) return rTag;
      }
    }

    return null;
  }

  public XmlTag findGroup(String name) {
    return findSpecialTag(name,"group",myTag, this, null);
  }

  public XmlTag findAttributeGroup(String name) {
    return findSpecialTag(name,"attributeGroup",myTag,this, null);
  }

  private Map<String,List<XmlTag>> mySubstitutions;

  public XmlElementDescriptor[] getSubstitutes(String localName, String namespace) {
    List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();

    if (mySubstitutions ==null) {
      mySubstitutions = new HashMap<String, List<XmlTag>>();

      XmlTag[] tags = myTag.getSubTags();

      for (XmlTag tag : tags) {
        if (equalsToSchemaName(tag, ELEMENT_TAG_NAME)) {
          final String substAttr = tag.getAttributeValue("substitutionGroup");
          if (substAttr != null) {
            String substLocalName = XmlUtil.findLocalNameByQualifiedName(substAttr);
            List<XmlTag> list = mySubstitutions.get(substLocalName);
            if (list == null) {
              list = new LinkedList<XmlTag>();
              mySubstitutions.put(substLocalName, list);
            }
            list.add(tag);
          }
        }
      }
    }

    List<XmlTag> substitutions = mySubstitutions.get(localName);
    if (substitutions==null) return XmlElementDescriptor.EMPTY_ARRAY;
    for (XmlTag tag : substitutions) {
      final String substAttr = tag.getAttributeValue("substitutionGroup");
      if (substAttr != null && checkElementNameEquivalence(localName, namespace, substAttr, tag)) {
        result.add(createElementDescriptor(tag));
      }
    }

    return result.toArray(new XmlElementDescriptor[result.size()]);
  }

  public static String getSchemaNamespace(XmlFile file) {
    return XmlUtil.findNamespacePrefixByURI(file, "http://www.w3.org/2001/XMLSchema");
  }

  public PsiElement getDeclaration(){
    return myFile.getDocument();
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return PsiScopesUtil.walkChildrenScopes(context, processor, substitutor, lastElement, place);
  }

  public String getName(PsiElement context){
    return getName();
  }

  public String getName(){
    return "";
  }

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

  }

  public Object[] getDependences(){
    return new Object[]{myFile, };
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

  public void validate(PsiElement context, Validator.ValidationHost host) {
    ExternalDocumentValidator.doValidation(context,host);
  }

  protected boolean supportsStdAttributes() {
    return true;
  }

  public XmlTag getTag() {
    return myTag;
  }
}
