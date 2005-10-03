package com.intellij.xml.util;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.ant.AntPropertyDeclaration;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;

/**
 * @author Mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class XmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.XmlUtil");

  public static final String TAGLIB_1_1_URI = "http://java.sun.com/j2ee/dtds/web-jsptaglibrary_1_1.dtd";
  public static final String TAGLIB_1_2_URI = "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd";
  public static final String TAGLIB_1_2_a_URI = "http://java.sun.com/j2ee/dtds/web-jsptaglibrary_1_2.dtd";
  public static final String TAGLIB_1_2_b_URI = "http://java.sun.com/JSP/TagLibraryDescriptor";
  public static final String TAGLIB_2_0_URI = "http://java.sun.com/xml/ns/j2ee";
  public static final String TAGLIB_2_1_URI = "http://java.sun.com/xml/ns/javaee";

  public static final String XML_SCHEMA_URI = "http://www.w3.org/2001/XMLSchema";
  public static final String XML_SCHEMA_URI2 = "http://www.w3.org/1999/XMLSchema";
  public static final String XML_SCHEMA_URI3 = "http://www.w3.org/2000/10/XMLSchema";
  public static final String XML_SCHEMA_INSTANCE_URI = "http://www.w3.org/2001/XMLSchema-instance";
  public static final String ANT_URI = "http://ant.apache.org/schema.xsd";
  public static final String XHTML_URI = "http://www.w3.org/1999/xhtml";
  public static final String HTML_URI = "http://www.w3.org/1999/html";
  public static final String EMPTY_URI = "";
  public static final Key<String> TEST_PATH = Key.create("TEST PATH");
  public static final String JSP_URI = "http://java.sun.com/JSP/Page";
  public static final String ANY_URI = "http://www.intellij.net/ns/any";

  private static final String JSTL_CORE_URI = "http://java.sun.com/jsp/jstl/core";
  private static final String JSTL_CORE_URI2 = "http://java.sun.com/jstl/core";
  private static final String JSTL_CORE_URI3 = "http://java.sun.com/jstl/core_rt";
  public static final String[] JSTL_CORE_URIS = { JSTL_CORE_URI, JSTL_CORE_URI2, JSTL_CORE_URI3 };

  public static final String JSF_HTML_URI = "http://java.sun.com/jsf/html";

  private static final String JSTL_FORMAT_URI = "http://java.sun.com/jsp/jstl/fmt";
  private static final String JSTL_FORMAT_URI2 = "http://java.sun.com/jstl/fmt";
  private static final String JSTL_FORMAT_URI3 = "http://java.sun.com/jstl/fmt_rt";
  public static final String[] JSTL_FORMAT_URIS = { JSTL_FORMAT_URI, JSTL_FORMAT_URI2, JSTL_FORMAT_URI3 };

  public static final String SPRING_URI = "http://www.springframework.org/tags";
  public static final String STRUTS_BEAN_URI = "http://struts.apache.org/tags-bean";
  public static final String STRUTS_LOGIC_URI = "http://struts.apache.org/tags-logic";
  public static final String STRUTS_HTML_URI = "http://struts.apache.org/tags-html";
  public static final String SPRING_CORE_URI = "http://www.springframework.org/dtd/spring-beans.dtd";
  public static final String XSD_SIMPLE_CONTENT_TAG = "simpleContent";
  public static final @NonNls String NO_NAMESPACE_SCHEMA_LOCATION_ATT = "noNamespaceSchemaLocation";
  public static final @NonNls String SCHEMA_LOCATION_ATT = "schemaLocation";

  public static String getSchemaLocation(XmlTag tag, String namespace) {
    final String uri = ExternalResourceManagerEx.getInstanceEx().getResourceLocation(namespace);
    if (uri != null && !uri.equals(namespace)) return uri;

    while (true) {
      if ("".equals(namespace)) {
        final String attributeValue = tag.getAttributeValue("noNamespaceSchemaLocation", XML_SCHEMA_INSTANCE_URI);
        if(attributeValue != null) return attributeValue;
      }
      else {
        String schemaLocation = tag.getAttributeValue("schemaLocation", XML_SCHEMA_INSTANCE_URI);
        if (schemaLocation != null) {
          int start = schemaLocation.indexOf(namespace);
          if (start >= 0) {
            start += namespace.length();
            final StringTokenizer tokenizer = new StringTokenizer(schemaLocation.substring(start + 1));
            if (tokenizer.hasMoreTokens()) {
              return tokenizer.nextToken();
            }
            else {
              return null;
            }
          }
        }
      }
      if (tag.getParent() instanceof XmlTag) {
        tag = (XmlTag)tag.getParent();
      }
      else {
        break;
      }
    }
    return null;
  }

  public static String findNamespacePrefixByURI(XmlFile file, @NonNls String uri) {
    if (file == null) return null;
    final XmlDocument document = file.getDocument();
    if (document == null) return null;
    final XmlTag tag = document.getRootTag();
    if (tag == null) return null;

    for (XmlAttribute attribute : tag.getAttributes()) {
      if (attribute.getName().startsWith("xmlns:") &&
          attribute.getValue().equals(uri)) {
        String ns = attribute.getName().substring("xmlns:".length());
        return ns;
      }
      if ("xmlns".equals(attribute.getName()) && attribute.getValue().equals(uri)) return "";
    }

    return null;
  }

  public static String[] findNamespacesByURI(XmlFile file, String uri) {
    if (file == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlDocument document = file.getDocument();
    if (document == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlTag tag = document.getRootTag();
    if (tag == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    XmlAttribute[] attributes = tag.getAttributes();


    List<String> result = new ArrayList<String>();

    for (int i = 0; i < attributes.length; i++) {
      XmlAttribute attribute = attributes[i];
      if (attribute.getName().startsWith("xmlns:") &&
          attribute.getValue().equals(uri)) {
        result.add(attribute.getName().substring("xmlns:".length()));
      }
      if ("xmlns".equals(attribute.getName()) && attribute.getValue().equals(uri)) result.add("");
    }

    return result.toArray(new String[result.size()]);
  }

  public static String getXsiNamespace(XmlFile file) {
    return findNamespacePrefixByURI(file, XML_SCHEMA_INSTANCE_URI);
  }

  public static XmlFile findXmlFile(PsiFile base, String uri) {
    PsiFile result = null;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String data = base.getUserData(TEST_PATH);

      if (data == null && base.getOriginalFile() != null) {
        data = base.getOriginalFile().getUserData(TEST_PATH);
      }
      if (data != null) {
        String filePath = data + "/" + uri;
        final VirtualFile path = LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
        if (path != null) {
          result = base.getManager().findFile(path);
        }
      }
    }
    if (result == null) {
      result = PsiUtil.findRelativeFile(uri, base);
    }

    if (result == null && base instanceof JspFile) {
      result = JspManager.getInstance(base.getProject()).getTldFileByUri(uri,(JspFile)base);
    }

    if (result instanceof XmlFile) {
      XmlFile xmlFile = (XmlFile)result;
      return xmlFile;
    }
    if(base instanceof JspFile)
      return JspManager.getInstance(base.getProject()).getTldFileByUri(uri, (JspFile)base);
    return null;
  }

  public static XmlToken getTokenOfType(PsiElement element, IElementType type) {
    if (element == null) {
      return null;
    }

    PsiElement[] children = element.getChildren();

    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;

        if (token.getTokenType() == type) {
          return token;
        }
      }
    }

    return null;
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag) {
    return processXmlElements(element, processor, deepFlag, false);
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag, boolean wideFlag) {
    if (element == null) return true;
    PsiFile baseFile = element.getContainingFile();
    return _processXmlElements(element, processor, baseFile, deepFlag, wideFlag);
  }

  private static boolean _processXmlElements(PsiElement element,
                                             PsiElementProcessor processor,
                                             PsiFile targetFile,
                                             boolean deepFlag,
                                             boolean wideFlag) {
    if (deepFlag) if (!processor.execute(element)) return false;

    if (element instanceof XmlEntityRef) {
      XmlEntityRef ref = (XmlEntityRef)element;

      PsiElement newElement = parseEntityRef(targetFile, ref, true);
      if (newElement == null) return true;

      while (newElement != null) {
        if (!processElement(newElement, processor, targetFile, deepFlag, wideFlag)) return false;
        newElement = newElement.getNextSibling();
      }

      return true;
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!processElement(child, processor, targetFile, deepFlag, wideFlag) && !wideFlag) return false;
    }

    return true;
  }

  private static boolean processElement(PsiElement child,
                                        PsiElementProcessor processor,
                                        PsiFile targetFile,
                                        boolean deepFlag,
                                        boolean wideFlag) {
    if (deepFlag) {
      if (!_processXmlElements(child, processor, targetFile, true, wideFlag)) {
        return false;
      }
    }
    else {
      if (child instanceof XmlEntityRef) {
        if (!_processXmlElements(child, processor, targetFile, false, wideFlag)) return false;
      }
      else if (!processor.execute(child)) return false;
    }
    return true;
  }

  private static PsiElement parseEntityRef(PsiFile targetFile, XmlEntityRef ref, boolean cacheValue) {
    int type = getContextType(ref);

    {
      final XmlEntityDecl entityDecl = ref.resolve(targetFile);
      if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
    }

    PsiElement e = ref;
    while (e != null) {
      if (e.getUserData(XmlElement.ORIGINAL_ELEMENT) != null) {
        e = (PsiElement)e.getUserData(XmlElement.ORIGINAL_ELEMENT);
        final PsiFile f = e.getContainingFile();
        if (f != null) {
          final XmlEntityDecl entityDecl = ref.resolve(targetFile);
          if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
        }

        continue;
      }
      if (e instanceof PsiFile) {
        PsiFile refFile = (PsiFile)e;
        final XmlEntityDecl entityDecl = ref.resolve(refFile);
        if (entityDecl != null) return parseEntityDecl(entityDecl, targetFile, type, cacheValue, ref);
        break;
      }

      e = e.getParent();
    }
    return null;
  }

  private static int getContextType(XmlEntityRef ref) {
    int type = XmlEntityDecl.CONTEXT_GENERIC_XML;
    PsiElement temp = ref;
    while (temp != null) {
      if (temp instanceof XmlAttributeDecl) {
        type = XmlEntityDecl.CONTEXT_ATTRIBUTE_SPEC;
      }
      else if (temp instanceof XmlElementDecl) {
        type = XmlEntityDecl.CONTEXT_ELEMENT_CONTENT_SPEC;
      }
      else if (temp instanceof XmlAttlistDecl) {
        type = XmlEntityDecl.CONTEXT_ATTLIST_SPEC;
      }
      else if (temp instanceof XmlEntityDecl) {
        type = XmlEntityDecl.CONTEXT_ENTITY_DECL_CONTENT;
      }
      else if (temp instanceof XmlEnumeratedType) {
        type = XmlEntityDecl.CONTEXT_ENUMERATED_TYPE;
      }
      else {
        temp = temp.getContext();
        continue;
      }
      break;
    }
    return type;
  }

  private static final Key<CachedValue<PsiElement>> PARSED_DECL_KEY = Key.create("PARSED_DECL_KEY");

  private static PsiElement parseEntityDecl(final XmlEntityDecl entityDecl,
                                            final PsiFile targetFile,
                                            final int type,
                                            boolean cacheValue,
                                            final XmlEntityRef entityRef) {
    if (!cacheValue) return entityDecl.parse(targetFile, type, entityRef);

    CachedValue<PsiElement> value = entityRef.getUserData(PARSED_DECL_KEY);
//    return entityDecl.parse(targetFile, type);

    if (value == null) {
      value = entityDecl.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiElement>() {
        public CachedValueProvider.Result<PsiElement> compute() {
          final PsiElement res = entityDecl.parse(targetFile, type, entityRef);
          if (res == null) return new Result<PsiElement>(res, new Object[]{targetFile});
          return new CachedValueProvider.Result<PsiElement>(res, new Object[]{res.getUserData(XmlElement.DEPENDING_ELEMENT), entityDecl, targetFile, entityRef});
        }
      }, false);
      entityRef.putUserData(PARSED_DECL_KEY, value);
    }

    return value.getValue();
  }

  /**
   * add child to the parent according to DTD/Schema element ordering
   *
   * @return newly added child
   */
  public static XmlTag addChildTag(XmlTag parent, XmlTag child) throws IncorrectOperationException {
    return addChildTag(parent, child, -1);
  }

  public static XmlTag addChildTag(XmlTag parent, XmlTag child, int index) throws IncorrectOperationException {

    // bug in PSI: cannot add child to <tag/>
    if (parent.getSubTags().length == 0 && parent.getText().endsWith("/>")) {
      final PsiElementFactory factory = parent.getManager().getElementFactory();
      final String name = parent.getName();
      final String text = parent.getText();
      final XmlTag tag = factory.createTagFromText(text.substring(0, text.length() - 2) + "></" + name + ">");
      parent = (XmlTag)parent.replace(tag);
    }

    final XmlElementDescriptor parentDescriptor = parent.getDescriptor();
    final XmlTag[] subTags = parent.getSubTags();
    if (parentDescriptor == null || subTags.length == 0) return (XmlTag)parent.add(child);
    int subTagNum = -1;
    
    for (XmlElementDescriptor childElementDescriptor : parentDescriptor.getElementsDescriptors(parent)) {
      final String childElementName = childElementDescriptor.getName();
      int prevSubTagNum = subTagNum;
      while (subTagNum < subTags.length - 1 && subTags[subTagNum + 1].getName().equals(childElementName)) {
        subTagNum++;
      }
      if (childElementName.equals(child.getLocalName())) {
        // insert child just after anchor
        // insert into the position specified by index
        subTagNum = index == -1 || index > subTagNum - prevSubTagNum ? subTagNum : prevSubTagNum + index;
        return (XmlTag)(subTagNum == -1 ? parent.addBefore(child, subTags[0]) : parent.addAfter(child, subTags[subTagNum]));
      }
    }
    return (XmlTag)parent.add(child);
  }

  public static String getAttributeValue(XmlTag tag, String name) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (name.equals(attribute.getName())) return attribute.getValue();
    }
    return null;
  }

  public static XmlTag findOnAnyLevel(XmlTag root, String[] chain) {
    XmlTag curTag = root;
    for (String s : chain) {
      curTag = curTag.findFirstSubTag(s);
      if (curTag == null) return null;
    }

    return curTag;
  }

  public static XmlTag findSubTag(XmlTag rootTag, String path) {
    String[] pathElements = path.split("/");

    XmlTag curTag = rootTag;
    for (String curTagName : pathElements) {
      curTag = curTag.findFirstSubTag(curTagName);
      if (curTag == null) break;
    }
    return curTag;
  }

  public static XmlTag findSubTagWithValue(XmlTag rootTag, String tagName, String tagValue) {
    if (rootTag == null) return null;
    final XmlTag[] subTags = rootTag.findSubTags(tagName);
    for (XmlTag subTag : subTags) {
      if (subTag.getValue().getTrimmedText().equals(tagValue)) {
        return subTag;
      }
    }
    return null;
  }

  public static XmlTag findSubTagWithValueOnAnyLevel(XmlTag baseTag, String tagName, String tagValue) {
    final String baseValue = baseTag.getValue().getTrimmedText();
    if (baseValue != null && baseValue.equals(tagValue)) {
      return baseTag;
    }
    else {
      final XmlTag[] subTags = baseTag.getSubTags();
      for (XmlTag subTag : subTags) {
        // It will return the first entry of subtag in the tree
        final XmlTag subTagFound = findSubTagWithValueOnAnyLevel(subTag, tagName, tagValue);
        if (subTagFound != null) return subTagFound;

      }
    }
    return null;
  }

  // Read the function name and parameter names to find out what this function does... :-)
  public static XmlTag find(String subTag, String withValue, String forTag, XmlTag insideRoot) {
    final XmlTag[] forTags = insideRoot.findSubTags(forTag);
    
    for (XmlTag tag : forTags) {
      final XmlTag[] allTags = tag.findSubTags(subTag);
      
      for (int j = 0; j < allTags.length; j++) {
        XmlTag curTag = allTags[j];
        if (curTag.getName().equals(subTag) && curTag.getValue().getTrimmedText().equalsIgnoreCase(withValue)) {
          return tag;
        }
      }
    }

    return null;
  }

  public static boolean isInAntBuildFile(XmlFile file){
    if(file == null) return false;
    if (file.getCopyableUserData(XmlFile.ANT_BUILD_FILE) != null) {
      return true;
    }
    XmlDocument document = file.getDocument();
    if(document != null){
      XmlTag rootTag = document.getRootTag();
      if(rootTag != null){
        return ANT_URI.equals(rootTag.getNamespace());
      }
    }
    return false;
  }

  public static boolean isAntTargetDefinition(XmlAttribute attr) {
    if (!isInAntBuildFile((XmlFile)attr.getContainingFile())) return false;
    final XmlTag tag = attr.getParent();

    return tag.getName().equals("target") && attr.getName().equals("name");
  }

  public static boolean isAntPropertyDefinition(XmlAttribute attr) {
    final XmlTag parentTag = attr.getParent();
    if(parentTag != null){
      final PsiMetaData data = parentTag.getMetaData();
      if(data instanceof AntPropertyDeclaration){
        if(data.getDeclaration() == attr.getValueElement()) return true;
      }
    }
    return false;
  }

  public static String[][] getDefaultNamespaces(final XmlDocument document) {
    final XmlFile file = XmlUtil.getContainingFile(document);
    if (file != null && file.getCopyableUserData(XmlFile.ANT_BUILD_FILE) != null) {
      return new String[][]{new String[]{"", ANT_URI}};
    }

    XmlTag tag = document.getRootTag();
    if (tag == null) return new String[][]{new String[]{EMPTY_URI}};
    if ("project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
      if (tag.getAttributeValue("default") != null) {
        return new String[][]{new String[]{"", ANT_URI}};
      }
    }

    String namespace = getDtdUri(document);
    if (namespace != null) return new String[][]{new String[]{"", namespace}};

    if ("taglib".equals(tag.getName())) {
      return new String[][]{new String[]{"", TAGLIB_1_2_URI}};
    }

    if (file != null) {
      final FileType fileType = file.getFileType();

      if (fileType == StdFileTypes.HTML ||
          fileType == StdFileTypes.XHTML
          ) {
        return new String[][]{new String[]{"", XHTML_URI}};
      }
      else if (fileType == StdFileTypes.JSPX || fileType == StdFileTypes.JSP){
        return new String[][]{
          new String[]{"", XHTML_URI},
          new String[]{"jsp", JSP_URI}
        };
      }
    }

    return new String[][]{new String[]{"", EMPTY_URI}};
  }


  public static String getDtdUri(XmlDocument document) {
    if (document.getProlog() != null) {
      final XmlDoctype doctype = document.getProlog().getDoctype();
      if (doctype != null) {
        return doctype.getDtdUri();
      }
    }
    return null;
  }

  private static void computeTag(XmlTag tag, final Map<String,List<String>> tagsMap, final Map<String,List<MyAttributeInfo>> attributesMap) {
    if (tag == null) {
      return;
    }
    final String tagName = tag.getName();

    {
      List<MyAttributeInfo> list = attributesMap.get(tagName);
      if (list == null) {
        list = new ArrayList<MyAttributeInfo>();
        final XmlAttribute[] attributes = tag.getAttributes();
        for (int i = 0; i < attributes.length; i++) {
          final XmlAttribute attribute = attributes[i];
          list.add(new MyAttributeInfo(attribute.getName()));
        }
      }
      else {
        final XmlAttribute[] attributes = tag.getAttributes();
        Collections.sort((List)list);
        Arrays.sort(attributes, new Comparator() {
          public int compare(Object o1, Object o2) {
            return ((XmlAttribute)o1).getName().compareTo(((XmlAttribute)o2).getName());
          }
        });

        final Iterator<MyAttributeInfo> iter = list.iterator();
        int index = 0;
        list = new ArrayList<MyAttributeInfo>();
        while (iter.hasNext()) {
          final MyAttributeInfo info = iter.next();
          boolean requiredFlag = false;
          while (attributes.length > index) {
            if (info.compareTo(attributes[index]) != 0) {
              if (info.compareTo(attributes[index]) < 0) {
                break;
              }
              if (attributes[index].getValue() != null) list.add(new MyAttributeInfo(attributes[index].getName(), false));
              index++;
            }
            else {
              requiredFlag = true;
              index++;
              break;
            }
          }
          info.myRequired &= requiredFlag;
          list.add(info);
        }
        while (attributes.length > index) {
          if (attributes[index].getValue() != null) {
            list.add(new MyAttributeInfo(attributes[index++].getName(), false));
          }
          else {
            index++;
          }
        }
      }
      attributesMap.put(tagName, list);
    }
    {
      final List<String> tags = tagsMap.get(tagName) != null ? tagsMap.get(tagName) : new ArrayList<String>();
      tagsMap.put(tagName, tags);
      tag.processElements(new FilterElementProcessor(new ClassFilter(XmlTag.class)) {
        public void add(PsiElement element) {
          XmlTag tag = (XmlTag)element;
          if (!tags.contains(tag.getName())) {
            tags.add(tag.getName());
          }
          computeTag(tag, tagsMap, attributesMap);
        }
      }, tag);
    }
  }

  public static XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag) {
    XmlElementDescriptor elementDescriptor = null;
    final String type = xmlTag.getAttributeValue("type", XML_SCHEMA_INSTANCE_URI);

    if(type != null){
      final String namespaceByPrefix = findNamespaceByPrefix(findPrefixByQualifiedName(type), xmlTag);
      final XmlNSDescriptor typeDecr = xmlTag.getNSDescriptor(namespaceByPrefix, true);

      if(typeDecr instanceof XmlNSDescriptorImpl){
        final XmlNSDescriptorImpl schemaDescriptor = ((XmlNSDescriptorImpl)typeDecr);
        final XmlElementDescriptor descriptorByType = schemaDescriptor.getDescriptorByType(type, xmlTag);
        elementDescriptor = descriptorByType;
      }
    }

    return elementDescriptor;
  }

  public static void collectEnumerationValues(final XmlTag element, final HashSet<String> variants) {

    for (final XmlTag tag : element.getSubTags()) {
      final String localName = tag.getLocalName();

      if (localName.equals("enumeration")) {
        final String attributeValue = tag.getAttributeValue("value");
        if (attributeValue != null) variants.add(attributeValue);
      }
      else if (localName.equals("union")) {
        variants.clear();
        return;
      }
      else {
        collectEnumerationValues(tag, variants);
      }
    }
  }

  public static XmlTag createChildTag(final XmlTag xmlTag, String localName, String namespace, String bodyText, boolean enforceNamespacesDeep) {
    String qname;
    final String prefix = xmlTag.getPrefixByNamespace(namespace);
    if (prefix != null && prefix.length() > 0) {
      qname = prefix + ":" + localName;
    }
    else {
      qname = localName;
    }
    try {
      String tagStart = qname;
      if (xmlTag.getPrefixByNamespace(namespace) == null) {
        tagStart += " xmlns=\"" + namespace + "\"";
      }
      XmlTag retTag;
      if (bodyText != null && bodyText.length() > 0) {
        retTag = xmlTag.getManager().getElementFactory().createTagFromText("<" + tagStart + ">" + bodyText + "</" + qname + ">");
        if (enforceNamespacesDeep) {
          retTag.acceptChildren(new PsiRecursiveElementVisitor() {
            public void visitXmlTag(XmlTag tag) {
              final String namespacePrefix = tag.getNamespacePrefix();
              if (namespacePrefix.length() == 0 || xmlTag.getNamespaceByPrefix(namespacePrefix) == null) {
                String qname;
                if (prefix != null && prefix.length() > 0) {
                  qname = prefix + ":" + tag.getLocalName();
                }
                else {
                  qname = tag.getLocalName();
                }
                try {
                  tag.setName(qname);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
              super.visitXmlTag(tag);
            }
          });
        }
      }
      else {
        retTag = xmlTag.getManager().getElementFactory().createTagFromText("<" + tagStart + "/>");
      }
      return retTag;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  public static Pair<XmlTagChild, XmlTagChild> findTagChildrenInRange(final PsiFile file, int startOffset, int endOffset) {
    PsiElement elementAtStart = file.findElementAt(startOffset);
    PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
    if (elementAtStart instanceof PsiWhiteSpace) {
      startOffset = elementAtStart.getTextRange().getEndOffset();
      elementAtStart = file.findElementAt(startOffset);
    }
    if (elementAtEnd instanceof PsiWhiteSpace) {
      endOffset = elementAtEnd.getTextRange().getStartOffset();
      elementAtEnd = file.findElementAt(endOffset - 1);
    }
    if (elementAtStart == null || elementAtEnd == null) return null;

    XmlTagChild first = PsiTreeUtil.getParentOfType(elementAtStart, XmlTagChild.class);
    if (first == null) return null;

    if (first.getTextRange().getStartOffset() != startOffset) {
      //Probably 'first' starts with whitespace
      PsiElement elementAt = file.findElementAt(first.getTextRange().getStartOffset());
      if (!(elementAt instanceof PsiWhiteSpace) || elementAt.getTextRange().getEndOffset() != startOffset) return null;
    }

    XmlTagChild last = first;
    while (last != null && last.getTextRange().getEndOffset() < endOffset) {
      last = PsiTreeUtil.getNextSiblingOfType(last, XmlTagChild.class);
    }

    if (last == null) return null;
    if (last.getTextRange().getEndOffset() != elementAtEnd.getTextRange().getEndOffset()) {
      //Probably 'last' ends with whitespace
      PsiElement elementAt = file.findElementAt(last.getTextRange().getEndOffset() - 1);
      if (!(elementAt instanceof PsiWhiteSpace) || elementAt.getTextRange().getStartOffset() != endOffset) {
        return null;
      }
    }

    return new Pair<XmlTagChild, XmlTagChild>(first, last);
  }

  public static boolean isSimpleXmlAttributeValue(final String unquotedValue) {
    for(int i = 0; i < unquotedValue.length(); ++i) {
      final char ch = unquotedValue.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != ':' && ch != '-') {
        return false;
      }
    }
    return true;
  }

  public static final boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static final boolean toCode(char ch) {
    return "<&>\u00a0".indexOf(ch) >= 0;
  }

  private static class MyAttributeInfo implements Comparable {
    boolean myRequired = true;
    String myName = null;

    MyAttributeInfo(String name) {
      myName = name;
    }

    MyAttributeInfo(String name, boolean flag) {
      myName = name;
      myRequired = flag;
    }

    public int compareTo(Object o) {
      if (o instanceof MyAttributeInfo) {
        return myName.compareTo(((MyAttributeInfo)o).myName);
      }
      else if (o instanceof XmlAttribute) {
        return myName.compareTo(((XmlAttribute)o).getName());
      }
      return -1;
    }
  }

  public static String generateDocumentDTD(XmlDocument doc) {
    final StringBuffer buffer = new StringBuffer();
    final Map<String,List<String>> tags = new HashMap<String, List<String>>();
    final Map<String,List<MyAttributeInfo>> attributes = new HashMap<String, List<MyAttributeInfo>>();
    computeTag(doc.getRootTag(), tags, attributes);
    final Iterator<String> iter = tags.keySet().iterator();
    while (iter.hasNext()) {
      final String tagName = iter.next();
      buffer.append(generateElementDTD(tagName, tags.get(tagName), attributes.get(tagName)));
    }
    return buffer.toString();
  }

  public static String generateElementDTD(String name, List<String> tags, List<MyAttributeInfo> attributes) {
    if (name == null || "".equals(name)) return "";
    if (name.endsWith(CompletionUtil.DUMMY_IDENTIFIER.trim())) return "";

    final StringBuffer buffer = new StringBuffer();
    {
      buffer.append("<!ELEMENT " + name + " ");
      if (tags.isEmpty()) {
        buffer.append("(#PCDATA)>\n");
      }
      else {
        buffer.append("(");
        final Iterator<String> iter = tags.iterator();
        while (iter.hasNext()) {
          final String tagName = iter.next();
          buffer.append(tagName);
          if (iter.hasNext()) {
            buffer.append("|");
          }
          else {
            buffer.append(")*");
          }
        }
        buffer.append(">\n");
      }
    }
    {
      if (!attributes.isEmpty()) {
        buffer.append("<!ATTLIST " + name);
        final Iterator<MyAttributeInfo> iter = attributes.iterator();
        while (iter.hasNext()) {
          final MyAttributeInfo info = iter.next();
          buffer.append("\n    " + generateAttributeDTD(info));
        }
        buffer.append(">\n");
      }
    }
    return buffer.toString();
  }

  private static String generateAttributeDTD(MyAttributeInfo info) {
    if (info.myName.endsWith(CompletionUtil.DUMMY_IDENTIFIER.trim())) return "";
    final StringBuffer buffer = new StringBuffer();
    buffer.append(info.myName + " ");
    //if ("id".equals(info.myName)) {
    //  buffer.append("ID");
    //}
    //else if ("ref".equals(info.myName)) {
    //  buffer.append("IDREF");
    //} else {
      buffer.append("CDATA");
    //}
    if (info.myRequired) {
      buffer.append(" #REQUIRED");
    }
    else {
      buffer.append(" #IMPLIED");
    }
    return buffer.toString();
  }

  public static String trimLeadingSpacesInMultilineTagValue(String tagValue) {
    return tagValue == null ? null : tagValue.replaceAll("\n\\s*", "\n");
  }

  public static String findNamespaceByPrefix(final String prefix, XmlTag contextTag) {
    final String s = contextTag.getNamespaceByPrefix(prefix);
    if (s != null) return s;
    return EMPTY_URI;
  }

  public static final String findPrefixByQualifiedName(String name) {
    final int prefixEnd = name.indexOf(':');
    if (prefixEnd > 0) {
      return name.substring(0, prefixEnd);
    }
    return "";
  }

  public static final String findLocalNameByQualifiedName(String name) {
    return name.substring(name.indexOf(':') + 1);
  }


  public static String escapeString(String str) {
    if (str == null) return null;
    StringBuffer buffer = null;
    for (int i = 0; i < str.length(); i++) {
      String entity;
      char ch = str.charAt(i);
      switch (ch) {
        case '\"':
          entity = "&quot;";
          break;
        case '<':
          entity = "&lt;";
          break;
        case '>':
          entity = "&gt;";
          break;
        case '&':
          entity = "&amp;";
          break;
        default :
          entity = null;
          break;
      }
      if (buffer == null) {
        if (entity != null) {
          // An entity occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuffer(str.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(str.substring(0, i));
          buffer.append(entity);
        }
      }
      else {
        if (entity == null) {
          buffer.append(ch);
        }
        else {
          buffer.append(entity);
        }
      }
    }

    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? str : buffer.toString();
  }

  public static XmlFile getContainingFile(PsiElement element) {
    while (!(element instanceof XmlFile) && element != null) {
      element = element.getContext();
    }
    return (XmlFile)element;
  }

  public static String getSubTagValue(XmlTag tag, final String subTagName) {
    final XmlTag subTag = tag.findFirstSubTag(subTagName);
    if (subTag != null) {
      return subTag.getValue().getTrimmedText();
    }
    return null;
  }

  public static int getStartOffsetInFile(XmlTag xmlTag) {
    int off = 0;
    while (true) {
      off += xmlTag.getStartOffsetInParent();
      final PsiElement parent = xmlTag.getParent();
      if (!(parent instanceof XmlTag)) break;
      xmlTag = (XmlTag)parent;
    }
    return off;
  }

  public static XmlElement setNewValue(XmlElement tag, String value) throws IncorrectOperationException {
    if (tag instanceof XmlTag) {
      ((XmlTag)tag).getValue().setText(value);
      return tag;
    }
    else if (tag instanceof XmlAttribute) {
      XmlAttribute attr = (XmlAttribute)tag;
      attr.setValue(value);
      return attr;
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  public static String decode(String text){
    if (text.length() == 0) return text;
    if(text.charAt(0) != '&' || text.length() < 3){
      if(text.indexOf('<') < 0 && text.indexOf('>') < 0) return text;
      return text.replaceAll("<!\\[CDATA\\[", "").replaceAll("\\]\\]>", "");
    }

    if(text.equals("&lt;")) {
      return "<";
    }
    if(text.equals("&gt;")) {
      return ">";
    }
    if(text.equals("&nbsp;")) {
      return "\u00a0";
    }
    if(text.equals("&amp;")) {
      return "&";
    }
    if(text.equals("&apos;")) {
      return "'";
    }
    if(text.equals("&quot;")) {
      return "\"";
    }
    if(text.startsWith("&quot;") && text.endsWith("&quot;")) {
      return "\"" + text.substring(6, text.length() - 6) + "\"";
    }
    if(text.startsWith("&#")) {
      text.substring(3, text.length() - 1);
      try{
        return "" + ((char)Integer.parseInt(text));
      }
      catch(NumberFormatException e){}
    }

    return text;
  }
  private static final String[] REPLACES = new String[]{
    "&lt;","<",
    "&nbsp;"," ",
    "&gt;",">",
    "&amp;","&",
    "&apos;","'",
    "&quot;","\"",
  };
  public static String unescape(String text){
    StringBuffer result = new StringBuffer(text.length());
    replace:
    for (int i=0; i<text.length();i++) {
      for (int j = 0; j < REPLACES.length; j+=2) {
        String toReplace = REPLACES[j];
        String replaceWith = REPLACES[j+1];

        final int len = toReplace.length();
        if (text.regionMatches(i, toReplace, 0, len)) {
          result.append(replaceWith);
          i += len-1;
          continue replace;
        }
      }
      result.append(text.charAt(i));
    }
    return result.toString();
  }
}
