// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.UriUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.StringPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.XmlTagFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.xml.XmlEntityCache;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.*;
import com.intellij.xml.index.IndexedRelevantResource;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XsdNamespaceBuilder;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

public final class XmlUtil {
  public static final @NonNls String XML_SCHEMA_URI = "http://www.w3.org/2001/XMLSchema";
  public static final @NonNls String XML_SCHEMA_URI2 = "http://www.w3.org/1999/XMLSchema";
  public static final @NonNls String XML_SCHEMA_URI3 = "http://www.w3.org/2000/10/XMLSchema";
  public static final String[] SCHEMA_URIS = {XML_SCHEMA_URI, XML_SCHEMA_URI2, XML_SCHEMA_URI3};
  public static final @NonNls String XML_SCHEMA_INSTANCE_URI = "http://www.w3.org/2001/XMLSchema-instance";
  public static final @NonNls String XML_SCHEMA_VERSIONING_URI = "http://www.w3.org/2007/XMLSchema-versioning";
  public static final @NonNls String XSLT_URI = "http://www.w3.org/1999/XSL/Transform";
  public static final @NonNls String XINCLUDE_URI = XmlPsiUtil.XINCLUDE_URI;
  public static final @NonNls String ANT_URI = "http://ant.apache.org/schema.xsd";
  public static final @NonNls String XHTML_URI = "http://www.w3.org/1999/xhtml";
  public static final @NonNls String HTML_URI = "http://www.w3.org/1999/html";
  public static final @NonNls String EMPTY_URI = "";

  // todo remove it
  public static final @NonNls Key<String> TEST_PATH = Key.create("TEST PATH");

  public static final @NonNls String TAGLIB_1_2_URI = "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd";
  public static final @NonNls String JSP_URI = "http://java.sun.com/JSP/Page";
  public static final @NonNls String JSTL_CORE_URI = "http://java.sun.com/jsp/jstl/core";
  public static final @NonNls String JSTL_CORE_URI2 = "http://java.sun.com/jstl/core";
  public static final @NonNls String JSTL_CORE_URI3 = "http://java.sun.com/jstl/core_rt";
  public static final @NonNls String JSTL_CORE_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsp/jstl/core";
  public static final @NonNls String[] JSTL_CORE_URIS = {JSTL_CORE_URI, JSTL_CORE_URI2, JSTL_CORE_URI3, JSTL_CORE_URI_JAVAEE_7};
  public static final @NonNls String JSF_HTML_URI = "http://java.sun.com/jsf/html";
  public static final @NonNls String JSF_HTML_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/html";

  public static final @NonNls String JSF_HTML_URI_JAKARTA_10 = "jakarta.faces.html";
  public static final @NonNls String[] JSF_HTML_URIS = {JSF_HTML_URI, JSF_HTML_URI_JAVAEE_7, JSF_HTML_URI_JAKARTA_10};
  public static final @NonNls String JSF_CORE_URI = "http://java.sun.com/jsf/core";
  public static final @NonNls String JSF_CORE_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/core";

  public static final @NonNls String JSF_CORE_URI_JAKARTA_10 = "jakarta.faces.core";
  public static final @NonNls String[] JSF_CORE_URIS = {JSF_CORE_URI, JSF_CORE_URI_JAVAEE_7, JSF_CORE_URI_JAKARTA_10};
  public static final @NonNls String JSF_PASS_THROUGH_ATTR_URI_JAVAEE7 = "http://xmlns.jcp.org/jsf";
  public static final @NonNls String JSF_PASSTHROUGH_URI = "http://xmlns.jcp.org/jsf/passthrough";

  public static final @NonNls String JSF_PASSTHROUGH_URI_JAKARTA_10 = "jakarta.faces.passthrough";

  public static final @NonNls String JSF_JAKARTA_TAGLIB_10 = "jakarta.faces";

  public static final @NonNls String JSF_JAKARTA_FACELETS_10 = "jakarta.faces.facelets";

  public static final @NonNls String JSF_JAKARTA_TAGS_TAGLIB_10 = "jakarta.tags.core";
  public static final @NonNls String JSF_JAKARTA_FUNCTIONS_TAGLIB_10 = "jakarta.tags.functions";
  public static final @NonNls String JSF_PASSTHROUGH_ATTR_URI_JAKARTA_10 = "jakarta.faces.passthrough";
  public static final @NonNls String JSTL_FORMAT_URI = "http://java.sun.com/jsp/jstl/fmt";
  public static final @NonNls String JSTL_FORMAT_URI2 = "http://java.sun.com/jstl/fmt";
  public static final @NonNls String SPRING_URI = "http://www.springframework.org/tags";
  public static final @NonNls String SPRING_FORMS_URI = "http://www.springframework.org/tags/form";
  public static final @NonNls String STRUTS_BEAN_URI = "http://struts.apache.org/tags-bean";
  public static final @NonNls String STRUTS_BEAN_URI2 = "http://jakarta.apache.org/struts/tags-bean";
  public static final @NonNls String APACHE_I18N_URI = "http://jakarta.apache.org/taglibs/i18n-1.0";
  public static final @NonNls String STRUTS_LOGIC_URI = "http://struts.apache.org/tags-logic";
  public static final @NonNls String STRUTS_HTML_URI = "http://struts.apache.org/tags-html";
  public static final @NonNls String STRUTS_HTML_URI2 = "http://jakarta.apache.org/struts/tags-html";
  public static final @NonNls String APACHE_TRINIDAD_URI = "http://myfaces.apache.org/trinidad";
  public static final @NonNls String APACHE_TRINIDAD_HTML_URI = "http://myfaces.apache.org/trinidad/html";
  public static final @NonNls String XSD_SIMPLE_CONTENT_TAG = "simpleContent";
  public static final @NonNls String NO_NAMESPACE_SCHEMA_LOCATION_ATT = "noNamespaceSchemaLocation";
  public static final @NonNls String SCHEMA_LOCATION_ATT = "schemaLocation";
  public static final @NonNls String[] WEB_XML_URIS =
    {"http://java.sun.com/xml/ns/j2ee", "http://java.sun.com/xml/ns/javaee", "http://xmlns.jcp.org/xml/ns/javaee",
      "http://java.sun.com/dtd/web-app_2_3.dtd",
      "http://java.sun.com/j2ee/dtds/web-app_2_2.dtd"};
  public static final @NonNls String FACELETS_URI = "http://java.sun.com/jsf/facelets";
  public static final @NonNls String FACELETS_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/facelets";

  public static final @NonNls String[] FACELETS_URIS = {FACELETS_URI, FACELETS_URI_JAVAEE_7, JSF_JAKARTA_FACELETS_10};
  public static final @NonNls String JSTL_FUNCTIONS_URI = "http://java.sun.com/jsp/jstl/functions";
  public static final @NonNls String JSTL_FUNCTIONS_URI2 = "http://java.sun.com/jstl/functions";
  public static final @NonNls String JSTL_FUNCTIONS_JAVAEE_7 = "http://xmlns.jcp.org/jsp/jstl/functions";
  public static final @NonNls String[] JSTL_FUNCTIONS_URIS = {JSTL_FUNCTIONS_URI, JSTL_FUNCTIONS_URI2};
  public static final @NonNls String JSTL_FN_FACELET_URI = "com.sun.facelets.tag.jstl.fn.JstlFnLibrary";
  public static final @NonNls String JSTL_CORE_FACELET_URI = "com.sun.facelets.tag.jstl.core.JstlCoreLibrary";
  public static final @NonNls String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";
  public static final @NonNls String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
  public static final List<String> ourSchemaUrisList = List.of(SCHEMA_URIS);
  public static final Key<Boolean> ANT_FILE_SIGN = new Key<>("FORCED ANT FILE");
  public static final @NonNls String TAG_DIR_NS_PREFIX = "urn:jsptagdir:";
  public static final @NonNls String VALUE_ATTR_NAME = "value";
  public static final @NonNls String ENUMERATION_TAG_NAME = "enumeration";
  public static final @NonNls String HTML4_LOOSE_URI = "http://www.w3.org/TR/html4/loose.dtd";
  public static final @NonNls String WSDL_SCHEMA_URI = "http://schemas.xmlsoap.org/wsdl/";
  public static final String XHTML4_SCHEMA_LOCATION;
  public static final ThreadLocal<Boolean> BUILDING_DOM_STUBS = ThreadLocal.withInitial(() -> Boolean.FALSE);
  private static final Logger LOG = Logger.getInstance(XmlUtil.class);
  private static final @NonNls String JSTL_FORMAT_URI3 = "http://java.sun.com/jstl/fmt_rt";
  public static final @NonNls String[] JSTL_FORMAT_URIS = {JSTL_FORMAT_URI, JSTL_FORMAT_URI2, JSTL_FORMAT_URI3};
  private static final @NonNls String FILE = "file:";
  private static final @NonNls String CLASSPATH = "classpath:/";
  private static final @NonNls String URN = "urn:";
  private static final Set<String> doNotVisitTags = Set.of("annotation", "element", "attribute");

  private XmlUtil() {
  }

  static {
    final URL xhtml4SchemaLocationUrl = XmlUtil.class.getResource(ExternalResourceManagerEx.STANDARD_SCHEMAS + "xhtml1-transitional.xsd");
    XHTML4_SCHEMA_LOCATION =
      VfsUtilCore.urlToPath(VfsUtilCore.toIdeaUrl(FileUtil.unquote(xhtml4SchemaLocationUrl.toExternalForm()), false));
  }

  public static @NotNull String getSchemaLocation(XmlTag tag, @NotNull String namespace) {
    while (tag != null) {
      String schemaLocation = tag.getAttributeValue(SCHEMA_LOCATION_ATT, XML_SCHEMA_INSTANCE_URI);
      if (schemaLocation != null) {
        StringTokenizer tokenizer = new StringTokenizer(schemaLocation);
        int i = 0;
        while (tokenizer.hasMoreTokens()) {
          String token = tokenizer.nextToken();
          if (i % 2 == 0 && namespace.equals(token) && tokenizer.hasMoreTokens()) {
            return tokenizer.nextToken();
          }
          i++;
        }
      }
      tag = tag.getParentTag();
    }
    return namespace;
  }

  public static @Nullable String findNamespacePrefixByURI(@NotNull XmlFile file, @NotNull @NonNls String uri) {
    final XmlTag tag = file.getRootTag();
    if (tag == null) return null;

    for (XmlAttribute attribute : tag.getAttributes()) {
      if (attribute.getName().startsWith("xmlns:") && uri.equals(attribute.getValue())) {
        return attribute.getName().substring("xmlns:".length());
      }
      if ("xmlns".equals(attribute.getName()) && uri.equals(attribute.getValue())) return "";
    }

    return null;
  }

  public static @Nullable XmlFile findNamespace(@NotNull PsiFile base, @NotNull String nsLocation) {
    final String location = ExternalResourceManager.getInstance().getResourceLocation(nsLocation, base.getProject());
    if (!location.equals(nsLocation)) { // is mapped
      return findXmlFile(base, location);
    }
    final XmlFile xmlFile = XmlSchemaProvider.findSchema(location, base);
    return xmlFile == null ? findXmlFile(base, location) : xmlFile;
  }

  public static @Nullable XmlFile findNamespaceByLocation(@NotNull PsiFile base, @NotNull String nsLocation) {
    final String location = ExternalResourceManager.getInstance().getResourceLocation(nsLocation, base.getProject());
    return findXmlFile(base, location);
  }

  public static @Unmodifiable @NotNull Collection<XmlFile> findNSFilesByURI(@NotNull String namespace, @NotNull Project project, @Nullable Module module) {
    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      resources = XmlNamespaceIndex.getResourcesByNamespace(namespace, project, module);
    final PsiManager psiManager = PsiManager.getInstance(project);
    return ContainerUtil.mapNotNull(resources,
                                    (NullableFunction<IndexedRelevantResource<String, XsdNamespaceBuilder>, XmlFile>)resource -> {
                                      PsiFile file = psiManager.findFile(resource.getFile());
                                      return file instanceof XmlFile ? (XmlFile)file : null;
                                    });
  }

  public static @Nullable XmlFile findXmlFile(@NotNull PsiFile base, @NotNull String uri) {
    PsiFile result = null;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String data = base.getOriginalFile().getUserData(TEST_PATH);

      if (data != null) {
        String filePath = data + "/" + uri;
        final VirtualFile path = StandardFileSystems.local().findFileByPath(filePath.replace(File.separatorChar, '/'));
        if (path != null) {
          result = base.getManager().findFile(path);
        }
      }
    }
    if (result == null) {
      result = findRelativeFile(uri, base);
    }

    if (result instanceof XmlFile) {
      return (XmlFile)result;
    }

    return null;
  }

  public static boolean isXmlToken(PsiElement element, @NotNull IElementType tokenType) {
    return element instanceof XmlToken && ((XmlToken)element).getTokenType() == tokenType;
  }

  public static @Nullable XmlToken getTokenOfType(PsiElement element, @NotNull IElementType type) {
    if (element == null) {
      return null;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      if (child instanceof XmlToken token) {

        if (token.getTokenType() == type) {
          return token;
        }
      }
    }

    return null;
  }

  public static boolean processXmlElements(@NotNull XmlElement element,
                                           @NotNull PsiElementProcessor<? super PsiElement> processor,
                                           boolean deepFlag) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag);
  }

  public static boolean processXmlElements(@NotNull XmlElement element,
                                           @NotNull PsiElementProcessor<? super PsiElement> processor,
                                           boolean deepFlag,
                                           boolean wideFlag) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag);
  }

  public static boolean processXmlElements(@NotNull XmlElement element,
                                           @NotNull PsiElementProcessor<? super PsiElement> processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag, baseFile);
  }

  public static boolean processXmlElements(@NotNull XmlElement element,
                                           @NotNull PsiElementProcessor<? super PsiElement> processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile,
                                           boolean processIncludes) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag, baseFile, processIncludes);
  }

  public static boolean processXmlElementChildren(@NotNull XmlElement element,
                                                  @NotNull PsiElementProcessor<? super PsiElement> processor,
                                                  final boolean deepFlag) {
    return XmlPsiUtil.processXmlElementChildren(element, processor, deepFlag);
  }

  public static boolean tagFromTemplateFramework(final @NotNull XmlTag tag) {
    final String ns = tag.getNamespace();
    return nsFromTemplateFramework(ns);
  }

  public static boolean nsFromTemplateFramework(final String ns) {
    return XSLT_URI.equals(ns) || XINCLUDE_URI.equals(ns);
  }

  public static char getCharFromEntityRef(@NonNls @NotNull String text) {
    try {
      if (text.charAt(1) != '#') {
        text = text.substring(1, text.length() - 1);
        char c = XmlTagUtil.getCharacterByEntityName(text);
        if (c == 0) {
          LOG.error("Unknown entity: " + text);
        }
        return c == 0 ? ' ' : c;
      }
      text = text.substring(2, text.length() - 1);
    }
    catch (StringIndexOutOfBoundsException e) {
      LOG.error("Cannot parse ref: '" + text + "'", e);
    }
    try {
      int code;
      if (StringUtil.startsWithChar(text, 'x')) {
        text = text.substring(1);
        code = Integer.parseInt(text, 16);
      }
      else {
        code = Integer.parseInt(text);
      }
      return (char)code;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public static boolean attributeFromTemplateFramework(final @NonNls String name, final XmlTag tag) {
    return "jsfc".equals(name) && isJsfHtmlScheme(tag);
  }

  public static @Nullable String getTargetSchemaNsFromTag(final @Nullable XmlTag xmlTag) {
    if (xmlTag == null) return null;
    String targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI2);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI3);
    return targetNamespace;
  }

  public static @Nullable XmlTag getSchemaSimpleContent(@NotNull XmlTag tag) {
    XmlElementDescriptor descriptor = tag.getDescriptor();

    if (descriptor instanceof XmlElementDescriptorImpl) {
      final TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType(tag);

      if (type instanceof ComplexTypeDescriptor) {
        final XmlTag[] simpleContent = new XmlTag[1];

        processXmlElements(((ComplexTypeDescriptor)type).getDeclaration(), element -> {
          if (element instanceof XmlTag tag1) {
            final @NonNls String s = tag1.getLocalName();

            if ((s.equals(XSD_SIMPLE_CONTENT_TAG) ||
                 s.equals("restriction") && "string".equals(findLocalNameByQualifiedName(tag1.getAttributeValue("base")))) &&
                tag1.getNamespace().equals(XML_SCHEMA_URI)) {
              simpleContent[0] = tag1;
              return false;
            }
          }

          return true;
        }, true);

        return simpleContent[0];
      }
    }
    return null;
  }

  public static <T extends PsiElement> void doDuplicationCheckForElements(final T[] elements,
                                                                          final Map<String, T> presentNames,
                                                                          DuplicationInfoProvider<? super T> provider,
                                                                          final Validator.ValidationHost host) {
    for (T t : elements) {
      final String name = provider.getName(t);
      if (name == null) continue;

      final String nameKey = provider.getNameKey(t, name);

      if (presentNames.containsKey(nameKey)) {
        final T psiElement = presentNames.get(nameKey);
        final String message = XmlPsiBundle.message("xml.inspections.duplicate.declaration", nameKey);

        if (psiElement != null) {
          presentNames.put(nameKey, null);

          host.addMessage(provider.getNodeForMessage(psiElement), message, Validator.ValidationHost.ErrorType.ERROR);
        }

        host.addMessage(provider.getNodeForMessage(t), message, Validator.ValidationHost.ErrorType.ERROR);
      }
      else {
        presentNames.put(nameKey, t);
      }
    }
  }

  public static boolean isAntFile(final PsiFile file) {
    if (file instanceof XmlFile xmlFile) {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && "project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
          if (tag.getAttributeValue("default") != null) {
            return true;
          }
          VirtualFile vFile = xmlFile.getOriginalFile().getVirtualFile();
          if (vFile != null && vFile.getUserData(ANT_FILE_SIGN) != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isJsfHtmlScheme(XmlTag tag) {
    for (String jsfHtmlUri : JSF_HTML_URIS) {
      if (tag.getNSDescriptor(jsfHtmlUri, true) != null) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable PsiFile findRelativeFile(String uri, PsiElement base) {
    if (base instanceof PsiFile baseFile) {
      VirtualFile file = UriUtil.findRelative(uri, baseFile.getOriginalFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }
    else if (base instanceof PsiDirectory baseDir) {
      VirtualFile file = UriUtil.findRelative(uri, baseDir);
      if (file == null) return null;
      return base.getManager().findFile(file);
    }

    return null;
  }

  public static void reformatTagStart(XmlTag tag) {
    ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (child == null) {
      CodeStyleManager.getInstance(tag.getProject()).reformat(tag);
    }
    else {
      CodeStyleManager.getInstance(tag.getProject())
        .reformatRange(tag, tag.getTextRange().getStartOffset(), child.getTextRange().getEndOffset());
    }
  }

  public static @Nullable XmlElementDescriptor getDescriptorFromContext(@NotNull XmlTag tag) {
    PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag parentTag) {
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null) {
        return XmlExtension.getExtension(tag.getContainingFile()).getElementDescriptor(tag, parentTag, parentDescriptor);
      }
    }
    return null;
  }

  public static void expandTag(@NotNull XmlTag tag) {
    XmlTag newTag = XmlElementFactory.getInstance(tag.getProject()).createTagFromText('<' + tag.getName() + "></" + tag.getName() + '>');

    ASTNode node = tag.getNode();
    if (!(node instanceof CompositeElement compositeElement)) return;

    final LeafElement emptyTagEnd = (LeafElement)XmlChildRole.EMPTY_TAG_END_FINDER.findChild(compositeElement);
    if (emptyTagEnd == null) return;

    if (XmlTokenType.WHITESPACES.contains(emptyTagEnd.getTreePrev().getElementType())) {
      compositeElement.removeChild(emptyTagEnd.getTreePrev());
    }
    compositeElement.removeChild(emptyTagEnd);
    PsiElement[] children = newTag.getChildren();

    compositeElement.addChildren(children[2].getNode(), null, null);
  }

  public static String getDefaultXhtmlNamespace(Project project) {
    final String doctype = ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(project);
    return Html5SchemaProvider.getHtml5SchemaLocation().equals(doctype)
           ? Html5SchemaProvider.getXhtml5SchemaLocation()
           : doctype;
  }

  public static CharSequence getLocalName(final CharSequence tagName) {
    int pos = StringUtil.indexOf(tagName, ':');
    if (pos == -1) {
      return tagName;
    }
    return tagName.subSequence(pos + 1, tagName.length());
  }

  public static boolean isStubBuilding() {
    return BUILDING_DOM_STUBS.get();
  }

  public static XmlTag addChildTag(XmlTag parent, XmlTag child, int index) throws IncorrectOperationException {

    // bug in PSI: cannot add child to <tag/>
    if (parent.getSubTags().length == 0 && parent.getText().endsWith("/>")) {
      final XmlElementFactory factory = XmlElementFactory.getInstance(parent.getProject());
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

  public static @NonNls String[] @Nullable [] getDefaultNamespaces(final XmlDocument document) {
    final XmlFile file = getContainingFile(document);

    final XmlTag tag = document.getRootTag();
    if (tag == null) return null;

    final @NotNull List<XmlFileNSInfoProvider> nsProviders = XmlFileNSInfoProvider.EP_NAME.getExtensionList();
    if (file != null) {

      NextProvider:
      for (XmlFileNSInfoProvider nsProvider : nsProviders) {
        final String[][] pairs = nsProvider.getDefaultNamespaces(file);
        if (pairs != null && pairs.length > 0) {

          for (final String[] nsMapping : pairs) {
            if (nsMapping == null || nsMapping.length != 2 || nsMapping[0] == null || nsMapping[1] == null) {
              LOG.debug("NSInfoProvider " + nsProvider + " gave wrong info about " + file.getVirtualFile());
              continue NextProvider;
            }
          }
          return pairs;
        }
      }
    }

    String namespace = getDtdUri(document);

    if (namespace != null) {
      boolean overrideNamespaceFromDocType = false;

      if (file != null) {
        for (XmlFileNSInfoProvider provider : nsProviders) {
          try {
            if (provider.overrideNamespaceFromDocType(file)) {
              overrideNamespaceFromDocType = true;
              break;
            }
          }
          catch (AbstractMethodError ignored) {
          }
        }
      }

      if (!overrideNamespaceFromDocType) return new String[][]{new String[]{"", namespace}};
    }

    if ("taglib".equals(tag.getName())) {
      return new String[][]{new String[]{"", TAGLIB_1_2_URI}};
    }

    if (file != null) {

      final Language language = file.getLanguage();
      if (language.isKindOf(HTMLLanguage.INSTANCE) || language == XHTMLLanguage.INSTANCE) {
        return new String[][]{new String[]{"", XHTML_URI}};
      }
    }

    return null;
  }

  public static @Nullable String getDtdUri(XmlDocument document) {
    XmlProlog prolog = document.getProlog();
    if (prolog != null) {
      return getDtdUri(prolog.getDoctype());
    }
    return null;
  }

  public static @Nullable String getDtdUri(XmlDoctype doctype) {
    if (doctype != null) {
      String docType = doctype.getDtdUri();
      if (docType == null) {
        final String publicId = doctype.getPublicId();
        if (PsiTreeUtil.getParentOfType(doctype, XmlDocument.class) instanceof HtmlDocumentImpl &&
            publicId != null && publicId.contains("-//W3C//DTD ")) {
          return guessDtdByPublicId(publicId);
        }
        else if (HtmlUtil.isHtml5Doctype(doctype)) {
          docType = doctype.getLanguage() instanceof HTMLLanguage
                    ? Html5SchemaProvider.getHtml5SchemaLocation()
                    : Html5SchemaProvider.getXhtml5SchemaLocation();
        }
      }
      return docType;
    }
    return null;
  }

  private static String guessDtdByPublicId(String id) {
    if (id.contains("XHTML")) {
      if (id.contains("1.1")) {
        if (id.contains("Basic")) {
          return "http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd";
        }
        return "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd";
      }
      else {
        if (id.contains("Strict")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd";
        }
        else if (id.contains("Frameset")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd";
        }
        else if (id.contains("Transitional")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd";
        }
      }
    }
    else if (id.contains("HTML")) {
      if (id.contains("Strict")) {
        return "http://www.w3.org/TR/html4/strict.dtd";
      }
      else if (id.contains("Frameset")) {
        return "http://www.w3.org/TR/html4/frameset.dtd";
      }
      return HTML4_LOOSE_URI;
    }
    return null;
  }

  private static void computeTag(XmlTag tag,
                                 final Map<String, List<String>> tagsMap,
                                 final Map<String, List<MyAttributeInfo>> attributesMap,
                                 final boolean processIncludes) {
    if (tag == null) {
      return;
    }
    final String tagName = tag.getName();

    List<MyAttributeInfo> list = attributesMap.get(tagName);
    if (list == null) {
      list = new ArrayList<>();
      final XmlAttribute[] attributes = tag.getAttributes();
      for (final XmlAttribute attribute : attributes) {
        list.add(new MyAttributeInfo(attribute.getName()));
      }
    }
    else {
      final XmlAttribute[] attributes = tag.getAttributes().clone();
      list.sort(null);
      Arrays.sort(attributes, Comparator.comparing(XmlAttribute::getName));

      final Iterator<MyAttributeInfo> iter = list.iterator();
      list = new ArrayList<>();
      int index = 0;
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
    final List<String> tags = tagsMap.get(tagName) != null ? tagsMap.get(tagName) : new ArrayList<>();
    tagsMap.put(tagName, tags);
    PsiFile file = tag.isValid() ? tag.getContainingFile() : null;
    processXmlElements(tag, new FilterElementProcessor(XmlTagFilter.INSTANCE) {
      @Override
      public void add(PsiElement element) {
        XmlTag tag = (XmlTag)element;
        if (!tags.contains(tag.getName())) {
          tags.add(tag.getName());
        }
        computeTag(tag, tagsMap, attributesMap, processIncludes);
      }
    }, false, false, file, processIncludes);
    /*tag.processElements(new FilterElementProcessor(XmlTagFilter.INSTANCE) {
      public void add(PsiElement element) {
        XmlTag tag = (XmlTag)element;
        if (!tags.contains(tag.getName())) {
          tags.add(tag.getName());
        }
        computeTag(tag, tagsMap, attributesMap);
      }
    }, tag);*/
  }

  public static @Nullable XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag) {
    return findXmlDescriptorByType(xmlTag, null);
  }

  public static @Nullable XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag, @Nullable XmlTag context) {
    String type = xmlTag.getAttributeValue("type", XML_SCHEMA_INSTANCE_URI);

    if (type == null) {
      String ns = xmlTag.getNamespace();
      if (ourSchemaUrisList.contains(ns)) {
        type = xmlTag.getAttributeValue("type", null);
      }
    }

    XmlElementDescriptor elementDescriptor = null;
    if (type != null) {
      final String namespaceByPrefix = findNamespaceByPrefix(findPrefixByQualifiedName(type), xmlTag);
      XmlNSDescriptor typeDecr = xmlTag.getNSDescriptor(namespaceByPrefix, true);

      if (typeDecr == null && namespaceByPrefix.isEmpty()) {
        if (context != null) typeDecr = context.getNSDescriptor("", true);

        if (typeDecr == null) {
          final PsiFile containingFile = xmlTag.getContainingFile();
          if (containingFile instanceof XmlFile) {
            final XmlDocument document = ((XmlFile)containingFile).getDocument();
            if (document != null) typeDecr = (XmlNSDescriptor)document.getMetaData();
          }
        }
      }

      if (typeDecr instanceof XmlNSDescriptorImpl schemaDescriptor) {
        elementDescriptor = schemaDescriptor.getDescriptorByType(type, xmlTag);
      }
    }

    return elementDescriptor;
  }

  public static boolean collectEnumerationValues(final XmlTag element, final HashSet<? super String> variants) {
    return processEnumerationValues(element, xmlTag -> {
      variants.add(xmlTag.getAttributeValue(VALUE_ATTR_NAME));
      return true;
    });
  }

  /**
   * @return true if enumeration is exhaustive
   */
  public static boolean processEnumerationValues(final XmlTag element, final Processor<? super XmlTag> tagProcessor) {
    return processEnumerationValues(element, tagProcessor, new HashSet<>());
  }

  private static boolean processEnumerationValues(XmlTag element, Processor<? super XmlTag> tagProcessor, Set<? super XmlTag> visited) {
    if (!visited.add(element)) return true;
    boolean exhaustiveEnum = true;

    for (final XmlTag tag : element.getSubTags()) {
      final @NonNls String localName = tag.getLocalName();

      if (localName.equals(ENUMERATION_TAG_NAME)) {
        final String attributeValue = tag.getAttributeValue(VALUE_ATTR_NAME);
        if (attributeValue != null) {
          if (!tagProcessor.process(tag)) {
            return exhaustiveEnum;
          }
        }
      }
      else if (localName.equals("union")) {
        exhaustiveEnum = false;
        processEnumerationValues(tag, tagProcessor, visited);
        XmlAttribute attribute = tag.getAttribute("memberTypes");
        if (attribute != null && attribute.getValueElement() != null) {
          for (PsiReference reference : attribute.getValueElement().getReferences()) {
            PsiElement resolve = reference.resolve();
            if (resolve instanceof XmlTag) {
              processEnumerationValues((XmlTag)resolve, tagProcessor, visited);
            }
          }
        }
      }
      else if (localName.equals("extension")) {
        XmlTag base = XmlSchemaTagsProcessor.resolveTagReference(tag.getAttribute("base"));
        if (base != null) {
          return processEnumerationValues(base, tagProcessor, visited);
        }
      }
      else if (!doNotVisitTags.contains(localName)) {
        // don't go into annotation
        exhaustiveEnum &= processEnumerationValues(tag, tagProcessor, visited);
      }
    }
    return exhaustiveEnum;
  }

  /**
   * @param bodyText pass null to create collapsed tag, empty string means creating expanded one
   */
  public static XmlTag createChildTag(@NotNull XmlTag xmlTag,
                                      @NotNull String localName,
                                      @Nullable String namespace,
                                      @Nullable String bodyText,
                                      boolean enforceNamespacesDeep,
                                      @NotNull Function<String, XmlTag> tagFromTextFactory) {
    String qname;
    final String prefix = xmlTag.getPrefixByNamespace(namespace);
    if (prefix != null && !prefix.isEmpty()) {
      qname = prefix + ":" + localName;
    }
    else {
      qname = localName;
    }
    try {
      String tagStart = qname + (!StringUtil.isEmpty(namespace) && xmlTag.getPrefixByNamespace(namespace) == null &&
                                 !(StringUtil.isEmpty(xmlTag.getNamespacePrefix()) && namespace.equals(xmlTag.getNamespace()))
                                 ? " xmlns=\"" + namespace + "\""
                                 : "");
      XmlTag retTag;
      if (bodyText != null) {
        retTag = tagFromTextFactory.apply("<" + tagStart + ">" + bodyText + "</" + qname + ">");
        if (enforceNamespacesDeep) {
          retTag.acceptChildren(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
              final String namespacePrefix = tag.getNamespacePrefix();
              if (namespacePrefix.isEmpty()) {
                String qname;
                if (prefix != null && !prefix.isEmpty()) {
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
        retTag = tagFromTextFactory.apply("<" + tagStart + "/>");
      }
      return retTag;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  public static @Nullable Pair<XmlTagChild, XmlTagChild> findTagChildrenInRange(final PsiFile file, int startOffset, int endOffset) {
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

    return Pair.create(first, last);
  }

  public static boolean isSimpleValue(final @NotNull String unquotedValue, final PsiElement context) {
    for (int i = 0; i < unquotedValue.length(); ++i) {
      final char ch = unquotedValue.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != ':' && ch != '-') {
        final XmlFile file = PsiTreeUtil.getParentOfType(context, XmlFile.class);
        if (file != null) {
          XmlTag tag = file.getRootTag();
          return tag != null && !tagFromTemplateFramework(tag);
        }
      }
    }
    return true;
  }

  public static boolean toCode(@NotNull String str) {
    for (int i = 0; i < str.length(); i++) {
      if (toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static boolean toCode(char ch) {
    return "<&>\u00a0".indexOf(ch) >= 0;
  }

  public static @Nullable PsiNamedElement findRealNamedElement(final @NotNull PsiNamedElement _element) {
    PsiElement currentElement = _element;
    final XmlEntityRef lastEntityRef = PsiTreeUtil.getParentOfType(currentElement, XmlEntityRef.class);

    while (!(currentElement instanceof XmlFile)) {
      PsiElement dependingElement = currentElement.getUserData(XmlElement.DEPENDING_ELEMENT);
      if (dependingElement == null) dependingElement = currentElement.getContext();
      currentElement = dependingElement;
      if (dependingElement == null) break;
    }

    if (currentElement != null) {
      final String name = _element.getName();
      if (_element instanceof XmlEntityDecl) {
        final XmlEntityDecl cachedEntity = XmlEntityCache.getCachedEntity((PsiFile)currentElement, name);
        if (cachedEntity != null) return cachedEntity;
      }

      final PsiNamedElement[] result = new PsiNamedElement[1];

      processXmlElements((XmlFile)currentElement, element -> {
        if (element instanceof PsiNamedElement) {
          final String elementName = ((PsiNamedElement)element).getName();

          if (elementName.equals(name) && _element.getClass().isInstance(element)
              || lastEntityRef != null && element instanceof XmlEntityDecl &&
                 elementName.equals(lastEntityRef.getText().substring(1, lastEntityRef.getTextLength() - 1))) {
            result[0] = (PsiNamedElement)element;
            return false;
          }
        }

        return true;
      }, true);

      return result[0];
    }

    return null;
  }

  public static int getPrefixLength(final @NotNull String s) {
    if (s.startsWith(TAG_DIR_NS_PREFIX)) return TAG_DIR_NS_PREFIX.length();
    if (s.startsWith(FILE)) return FILE.length();
    if (s.startsWith(CLASSPATH)) return CLASSPATH.length();
    return 0;
  }

  public static boolean isUrlText(final String s, Project project) {
    final boolean surelyUrl = HtmlUtil.hasHtmlPrefix(s) || s.startsWith(URN);
    if (surelyUrl) return true;
    int protocolIndex = s.indexOf(":/");
    if (protocolIndex > 1 && !s.regionMatches(0, "classpath", 0, protocolIndex)) return true;
    return !s.equals(ExternalResourceManager.getInstance().getResourceLocation(s, project));
  }

  public static String generateDocumentDTD(XmlDocument doc, boolean full) {
    final Map<String, List<String>> tags = new LinkedHashMap<>();
    final Map<String, List<MyAttributeInfo>> attributes = new LinkedHashMap<>();

    final XmlTag rootTag = doc.getRootTag();
    computeTag(rootTag, tags, attributes, full);

    // For supporting not well-formed XML
    for (PsiElement element = rootTag != null ? rootTag.getNextSibling() : null; element != null; element = element.getNextSibling()) {
      if (element instanceof XmlTag) {
        computeTag((XmlTag)element, tags, attributes, full);
      }
    }

    final StringBuilder buffer = new StringBuilder();
    for (final String tagName : tags.keySet()) {
      buffer.append(generateElementDTD(tagName, tags.get(tagName), attributes.get(tagName)));
    }
    return buffer.toString();
  }

  public static String generateElementDTD(String name, List<String> tags, List<? extends MyAttributeInfo> attributes) {
    if (name == null || name.isEmpty()) return "";
    if (name.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) return "";

    final @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("<!ELEMENT ").append(name).append(" ");
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
    if (!attributes.isEmpty()) {
      buffer.append("<!ATTLIST ").append(name);
      for (final MyAttributeInfo info : attributes) {
        buffer.append("\n    ").append(generateAttributeDTD(info));
      }
      buffer.append(">\n");
    }
    return buffer.toString();
  }

  private static String generateAttributeDTD(MyAttributeInfo info) {
    if (info.myName.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) return "";
    return info.myName + " " + "CDATA" + (info.myRequired ? " #REQUIRED" : " #IMPLIED");
  }

  public static String findNamespaceByPrefix(final String prefix, XmlTag contextTag) {
    return contextTag.getNamespaceByPrefix(prefix);
  }

  public static @NotNull String findPrefixByQualifiedName(@NotNull String name) {
    final int prefixEnd = name.indexOf(':');
    if (prefixEnd > 0) {
      return name.substring(0, prefixEnd);
    }
    return "";
  }

  public static @Nullable String findLocalNameByQualifiedName(String name) {
    return name == null ? null : name.substring(name.indexOf(':') + 1);
  }

  public static XmlFile getContainingFile(PsiElement element) {
    while (!(element instanceof XmlFile) && element != null) {
      final PsiElement context = element.getContext();
      if (context == null) {
        //todo Dmitry Avdeev: either XmlExtension should work on any PsiFile (not just XmlFile), or you need to handle elements from JspJavaFile in some other way
        final XmlExtension extension = XmlExtension.getExtensionByElement(element);
        if (extension != null) {
          element = extension.getContainingFile(element);
        }
      }
      else {
        if (element == context) {
          LOG.error("Context==element: " + element.getClass());
          return null;
        }
        element = context;
      }
    }
    return (XmlFile)element;
  }

  public static @NotNull String unescape(@NotNull String text) {
    return StringUtil.unescapeXmlEntities(text);
  }

  public static @NotNull String escape(@NotNull String text) {
    return StringUtil.escapeXmlEntities(text);
  }

  public static boolean isValidTagNameChar(char c) {
    return Character.isLetter(c) || Character.isDigit(c) ||
           c == ':' || c == '_' || c == '-' || c == '.';
  }

  public static @Nullable String extractXmlEncodingFromProlog(byte @NotNull [] content) {
    return XmlCharsetDetector.extractXmlEncodingFromProlog(content);
  }

  public static @Nullable String extractXmlEncodingFromProlog(@NotNull CharSequence text) {
    return XmlCharsetDetector.extractXmlEncodingFromProlog(text);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @NonNls String @Nullable [] attributeNames,
                                                                @Nullable ElementFilter elementFilter,
                                                                @NotNull PsiReferenceProvider provider) {
    registerXmlAttributeValueReferenceProvider(registrar, attributeNames, elementFilter, true, provider);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @NonNls String @Nullable [] attributeNames,
                                                                @Nullable ElementFilter elementFilter,
                                                                boolean caseSensitive,
                                                                @NotNull PsiReferenceProvider provider) {
    registerXmlAttributeValueReferenceProvider(registrar, attributeNames, elementFilter, caseSensitive, provider,
                                               PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @NonNls String @Nullable [] attributeNames,
                                                                @Nullable ElementFilter elementFilter,
                                                                boolean caseSensitive,
                                                                @NotNull PsiReferenceProvider provider,
                                                                double priority) {
    if (attributeNames == null) {
      registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(new FilterPattern(elementFilter)), provider, priority);
      return;
    }

    final StringPattern namePattern = caseSensitive
                                      ? StandardPatterns.string().oneOf(attributeNames)
                                      : StandardPatterns.string().oneOfIgnoreCase(attributeNames);
    registrar
      .registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName(namePattern).and(new FilterPattern(elementFilter)), provider,
                                 priority);
  }

  public static void registerXmlTagReferenceProvider(PsiReferenceRegistrar registrar,
                                                     @NonNls String[] names,
                                                     @Nullable ElementFilter elementFilter,
                                                     boolean caseSensitive,
                                                     @NotNull PsiReferenceProvider provider) {
    if (names == null) {
      registrar.registerReferenceProvider(XmlPatterns.xmlTag().and(new FilterPattern(elementFilter)), provider,
                                          PsiReferenceRegistrar.DEFAULT_PRIORITY);
      return;
    }


    final StringPattern namePattern =
      caseSensitive ? StandardPatterns.string().oneOf(names) : StandardPatterns.string().oneOfIgnoreCase(names);
    registrar.registerReferenceProvider(XmlPatterns.xmlTag().withLocalName(namePattern).and(new FilterPattern(elementFilter)), provider,
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  public static XmlFile findDescriptorFile(@NotNull XmlTag tag, @NotNull XmlFile containingFile) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    final XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    XmlFile descriptorFile = nsDescriptor != null
                             ? nsDescriptor.getDescriptorFile()
                             : containingFile.getDocument().getProlog().getDoctype() != null ? containingFile : null;
    if (nsDescriptor != null && (descriptorFile == null || descriptorFile.getName().equals(containingFile.getName() + ".dtd"))) {
      descriptorFile = containingFile;
    }
    return descriptorFile;
  }

  public static boolean isTagDefinedByNamespace(final @NotNull XmlTag xmlTag) {
    final XmlNSDescriptor nsDescriptor = xmlTag.getNSDescriptor(xmlTag.getNamespace(), false);
    final XmlElementDescriptor descriptor = nsDescriptor != null ? nsDescriptor.getElementDescriptor(xmlTag) : null;
    return descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor);
  }

  public static @Nullable XmlComment findPreviousComment(final PsiElement element) {
    PsiElement curElement = element;

    while (curElement != null && !(curElement instanceof XmlComment)) {
      curElement = curElement.getPrevSibling();
      if (curElement instanceof XmlText && StringUtil.isEmptyOrSpaces(curElement.getText())) {
        continue;
      }
      if (!(curElement instanceof PsiWhiteSpace) &&
          !(curElement instanceof XmlProlog) &&
          !(curElement instanceof XmlComment)
      ) {
        curElement = null; // finding comment fails, we found another similar declaration
        break;
      }
    }
    return (XmlComment)curElement;
  }

  public static boolean hasNonEditableInjectionFragmentAt(@NotNull XmlAttribute attribute, int offset) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(attribute.getProject());
    PsiElement host = manager.getInjectionHost(attribute);
    if (host == null) return false;
    Document doc = PsiDocumentManager.getInstance(attribute.getProject()).getDocument(attribute.getContainingFile());
    if (!(doc instanceof DocumentWindow)) return false;
    return ContainerUtil.exists(manager.getNonEditableFragments((DocumentWindow)doc), range -> {
      return range.getStartOffset() <= offset && offset <= (range.getEndOffset() + 1);
    });
  }

  public static boolean isNotInjectedOrCustomHtmlFile(@NotNull PsiFile file) {
    if (InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file) == null) return true;
    var language = file.getLanguage();
    return language instanceof HTMLLanguage
           && language != HTMLLanguage.INSTANCE;
  }

  public interface DuplicationInfoProvider<T extends PsiElement> {
    @Nullable
    String getName(@NotNull T t);

    @NotNull
    String getNameKey(@NotNull T t, @NotNull String name);

    @NotNull
    PsiElement getNodeForMessage(@NotNull T t);
  }

  private static class MyAttributeInfo implements Comparable {
    boolean myRequired = true;
    String myName;

    MyAttributeInfo(String name) {
      myName = name;
    }

    MyAttributeInfo(String name, boolean flag) {
      myName = name;
      myRequired = flag;
    }

    @Override
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
}
