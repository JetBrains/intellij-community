package com.intellij.xml.impl.schema;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;

import java.util.*;

/**
 * @author Mike
 */
public class XmlNSDescriptorImpl implements XmlNSDescriptor {
  private static final Set<String> STD_TYPES = new HashSet<String>();
  XmlFile myFile;
  private String myTargetNamespace;

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

  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    return getElementDescriptor(localName, namespace, new HashSet<XmlNSDescriptorImpl>());
  }

  private XmlElementDescriptor getElementDescriptor(String localName, String namespace, Set<XmlNSDescriptorImpl> visited) {
    if(visited.contains(this)) return null;
    final Pair<String, String> pair = new Pair<String, String>(namespace, localName);
    final CachedValue<XmlElementDescriptor> descriptor = myDescriptorsMap.get(pair);
    if(descriptor != null) return descriptor.getValue();
    XmlDocument document = myFile.getDocument();
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();

    for (int i = 0; i < tags.length; i++) {
      final XmlTag tag = tags[i];

      if (equalsToSchemaName(tag, "element")) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if(checkElementNameEquivalence(localName, namespace, name, tag)) {
            final CachedValue<XmlElementDescriptor> cachedValue = tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlElementDescriptor>() {
              public Result<XmlElementDescriptor> compute() {
                final XmlElementDescriptorImpl xmlElementDescriptor = new XmlElementDescriptorImpl(tag);
                return new Result<XmlElementDescriptor>(xmlElementDescriptor, xmlElementDescriptor.getDependences());
              }
            },false);
            myDescriptorsMap.put(pair, cachedValue);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, "include")) {
        final XmlAttribute schemaLocation = tag.getAttribute("schemaLocation", XmlUtil.ALL_NAMESPACE);
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation.getValue());
          if (xmlFile != null) {
            final XmlDocument includedDocument = xmlFile.getDocument();
            if (includedDocument != null) {
              final PsiMetaData data = includedDocument.getMetaData();
              if(data instanceof XmlNSDescriptorImpl){
                final XmlElementDescriptor elementDescriptor = ((XmlNSDescriptorImpl)data).getElementDescriptor(localName, namespace);
                if(elementDescriptor != null){
                  final CachedValue<XmlElementDescriptor> value = includedDocument.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlElementDescriptor>(){
                    public Result<XmlElementDescriptor> compute() {
                      return new Result<XmlElementDescriptor>(elementDescriptor, elementDescriptor.getDependences());
                    }
                  },false);
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

  private boolean checkElementNameEquivalence(String localName, String namespace, String fqn, XmlTag context){
    final String localAttrName = XmlUtil.findLocalNameByQualifiedName(fqn);
    if (!localAttrName.equals(localName)) return false;
    if(myTargetNamespace == null){
      final String attrNamespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(fqn));
      if(attrNamespace.equals(namespace))
        return true;
    }
    else return myTargetNamespace.equals(namespace);
    return false;
  }

  protected XmlAttributeDescriptorImpl getAttribute(String localName, String namespace) {
    XmlDocument document = myFile.getDocument();
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();

    for (int i = 0; i < tags.length; i++) {
      XmlTag tag = tags[i];

      if (equalsToSchemaName(tag, "attribute")) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          if (checkElementNameEquivalence(localName, namespace, name, tag)) {
            return new XmlAttributeDescriptorImpl(tag);
          }
        }
      }
    }

    return null;
  }

  protected TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    String type = descriptorTag.getAttributeValue("type");

    if (type != null) {
      return getTypeDescriptor(type, descriptorTag);
    }

    return findTypeDescriptor(descriptorTag, null);
  }

  protected TypeDescriptor getTypeDescriptor(final String name, XmlTag context) {
    if(checkSchemaNamespace(name, context) && STD_TYPES.contains(name)){
      return new StdTypeDescriptor(name);
    }

    final XmlDocument document = myFile.getDocument();
    if (document == null) return null;
    return findTypeDescriptor(document.getRootTag(), name);
  }

  public XmlElementDescriptor getDescriptorByType(String qName, XmlTag instanceTag){
    final XmlDocument document = myFile.getDocument();
    if(document == null) return null;
    final XmlTag tag = document.getRootTag();
    if(tag == null) return null;
    final TypeDescriptor typeDescriptor = findTypeDescriptor(tag, qName);
    if(!(typeDescriptor instanceof ComplexTypeDescriptor)) return null;
    return new XmlElementDescriptorByType(instanceTag, (ComplexTypeDescriptor)typeDescriptor);
  }

  private boolean checkSchemaNamespace(String name, XmlTag context){
    final String namespace = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(name));
    if(namespace != null && namespace.length() > 0){
      return XmlUtil.XML_SCHEMA_URI.equals(namespace);
    }
    return "xsd".equals(XmlUtil.findPrefixByQualifiedName(name));
  }

  private static boolean checkSchemaNamespace(XmlTag context){
    final String namespace = context.getNamespace();
    if(namespace != null && namespace.length() > 0){
      return XmlUtil.XML_SCHEMA_URI.equals(namespace);
    }
    return context.getName().startsWith("xsd:");
  }


  protected TypeDescriptor findTypeDescriptor(XmlTag rootTag, final String name) {
    final Pair<String, XmlTag> pair = new Pair<String, XmlTag>(name, rootTag);
    final CachedValue<TypeDescriptor> descriptor = myTypesMap.get(pair);
    if(descriptor != null) return descriptor.getValue();

    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();

    for (int i = 0; i < tags.length; i++) {
      final XmlTag tag = tags[i];

      if (equalsToSchemaName(tag, "complexType")) {
        if (name == null) {
          CachedValue<TypeDescriptor> value = createAndPutTypesCachedValue(tag, pair);
          return value.getValue();
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (nameAttribute != null) {
          if (nameAttribute.equals(name) || (name.indexOf(":") >= 0 && nameAttribute.equals(name.substring(name.indexOf(":") + 1)))) {
            CachedValue<TypeDescriptor> cachedValue = createAndPutTypesCachedValue(tag, pair);
            return cachedValue.getValue();
          }
        }
      }
      else if (equalsToSchemaName(tag, "simpleType")) {
        if (name == null) {
          return new SimpleTypeDescriptor(tag);
        }

        String nameAttribute = tag.getAttributeValue("name");

        if (name.equals(nameAttribute)
            || name.indexOf(":") >= 0 && name.substring(name.indexOf(":") + 1).equals(nameAttribute)) {
          return new SimpleTypeDescriptor(tag);
        }
      }
      else if (equalsToSchemaName(tag, "include")) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");
        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation);
          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();
            if (document != null) {
              final XmlTag rTag = document.getRootTag();

              final CachedValue<TypeDescriptor> value = tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<TypeDescriptor>() {
                public Result<TypeDescriptor> compute() {
                  final TypeDescriptor complexTypeDescriptor = findTypeDescriptor(rTag, name);
                  return new Result<TypeDescriptor>(complexTypeDescriptor, new Object[]{rTag});
                }
              }, false);
              if (value.getValue() != null) {
                myTypesMap.put(pair, value);
                return value.getValue();
              }
            }
          }
        }
      }
    }
    return null;
  }

  private CachedValue<TypeDescriptor> createAndPutTypesCachedValue(final XmlTag tag, final Pair<String, XmlTag> pair) {
    final CachedValue<TypeDescriptor> value = tag.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<TypeDescriptor>() {
      public CachedValueProvider.Result<TypeDescriptor> compute() {
        final ComplexTypeDescriptor complexTypeDescriptor = new ComplexTypeDescriptor(XmlNSDescriptorImpl.this, tag);
        return new Result<TypeDescriptor>(complexTypeDescriptor, new Object[]{tag});
      }
    }, false);
    myTypesMap.put(pair, value);
    return value;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    XmlElement parent = (XmlElement)tag.getParent();
    final String namespace = tag.getNamespace();
    while(parent instanceof XmlTag && !namespace.equals(((XmlTag)parent).getNamespace()))
      parent = (XmlElement)parent.getContext();
    if (parent instanceof XmlTag) {
      final XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = getElementDescriptor(parentTag);

      if(parentDescriptor != null){
        return parentDescriptor.getElementDescriptor(tag);
      }
      else{
        return null;
      }
    }
    else {
      return getElementDescriptor(tag.getLocalName(), tag.getNamespace());
    }
  }

  public XmlElementDescriptor[] getRootElementsDescriptors() {
    final List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();
    XmlDocument document = myFile.getDocument();
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return null;
    XmlTag[] tags = rootTag.getSubTags();

    for (int i = 0; i < tags.length; i++) {
      XmlTag tag = tags[i];

      if (equalsToSchemaName(tag, "element")) {
        String name = tag.getAttributeValue("name");

        if (name != null) {
          final XmlElementDescriptor elementDescriptor = getElementDescriptor(name, getDefaultNamespace());
          if(elementDescriptor != null)
            result.add(elementDescriptor);
        }
      }
    }

    return result.toArray(new XmlElementDescriptor[result.size()]);
  }

  protected static boolean equalsToSchemaName(XmlTag tag, String schemaName) {
    return schemaName.equals(tag.getLocalName()) && checkSchemaNamespace(tag);
  }

  private static XmlTag findSpecialTag(String name, String specialName, XmlTag rootTag) {
    XmlTag[] tags = rootTag.getSubTags();

    for (int i = 0; i < tags.length; i++) {
      XmlTag tag = tags[i];

      if (equalsToSchemaName(tag, specialName)) {
        String attribute = tag.getAttributeValue("name");

        if (name.equals(attribute)
            || name.indexOf(":") >= 0 && name.substring(name.indexOf(":") + 1).equals(attribute)) {
          return tag;
        }
      } else if (equalsToSchemaName(tag,"include")) {
        final String schemaLocation = tag.getAttributeValue("schemaLocation");

        if (schemaLocation != null) {
          final XmlFile xmlFile = XmlUtil.findXmlFile(rootTag.getContainingFile(), schemaLocation);

          if (xmlFile != null) {
            final XmlDocument document = xmlFile.getDocument();
            if (document != null) {
              final XmlTag rTag = findSpecialTag(name,specialName,document.getRootTag());

              if (rTag != null) return rTag;
            }
          }
        }
      }
    }

    return null;
  }

  public XmlTag findGroup(String name) {
    return findSpecialTag(name,"group",myFile.getDocument().getRootTag());
  }

  public XmlTag findAttributeGroup(String name) {
    return findSpecialTag(name,"attributeGroup",myFile.getDocument().getRootTag());
  }

  private Map<String,List<XmlTag>> substituions;

  public XmlElementDescriptor[] getSubstitutes(String localName, String namespace) {
    List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();

    if (substituions==null) {
      XmlDocument document = myFile.getDocument();
      substituions = new HashMap<String, List<XmlTag>>();
      XmlTag rootTag = document.getRootTag();

      XmlTag[] tags = rootTag.getSubTags();

      for (int i = 0; i < tags.length; i++) {
        XmlTag tag = tags[i];

        if (equalsToSchemaName(tag,"element")) {
          final String substAttr = tag.getAttributeValue("substitutionGroup");
          if (substAttr != null) {
            String substLocalName = XmlUtil.findLocalNameByQualifiedName(substAttr);
            List<XmlTag> list = substituions.get(substLocalName);
            if (list==null) {
              list = new LinkedList<XmlTag>();
              substituions.put(substLocalName,list);
            }
            list.add(tag);
          }
        }
      }
    }

    List<XmlTag> substitutions = substituions.get(localName);
    if (substitutions==null) return XmlElementDescriptor.EMPTY_ARRAY;
    for (Iterator<XmlTag> i=substitutions.iterator();i.hasNext();) {
      XmlTag tag = i.next();

      final String substAttr = tag.getAttributeValue("substitutionGroup");
      if (substAttr != null && checkElementNameEquivalence(localName, namespace, substAttr, tag)) {
        result.add(new XmlElementDescriptorImpl(tag));
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

    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag rootTag = document.getRootTag();
      if (rootTag != null) {
        myTargetNamespace = rootTag.getAttributeValue("targetNamespace");
      }
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
  }
}
