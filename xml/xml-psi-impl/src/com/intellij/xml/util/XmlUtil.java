/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.UriUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.index.IndexedRelevantResource;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XsdNamespaceBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * @author Mike
 */
public class XmlUtil {
  @NonNls public static final String XML_SCHEMA_URI = "http://www.w3.org/2001/XMLSchema";
  @NonNls public static final String XML_SCHEMA_URI2 = "http://www.w3.org/1999/XMLSchema";
  @NonNls public static final String XML_SCHEMA_URI3 = "http://www.w3.org/2000/10/XMLSchema";
  public static final String[] SCHEMA_URIS = {XML_SCHEMA_URI, XML_SCHEMA_URI2, XML_SCHEMA_URI3};
  @NonNls public static final String XML_SCHEMA_INSTANCE_URI = "http://www.w3.org/2001/XMLSchema-instance";
  @NonNls public static final String XML_SCHEMA_VERSIONING_URI = "http://www.w3.org/2007/XMLSchema-versioning";
  @NonNls public static final String XSLT_URI = "http://www.w3.org/1999/XSL/Transform";
  @NonNls public static final String XINCLUDE_URI = XmlPsiUtil.XINCLUDE_URI;
  @NonNls public static final String ANT_URI = "http://ant.apache.org/schema.xsd";
  @NonNls public static final String XHTML_URI = "http://www.w3.org/1999/xhtml";
  @NonNls public static final String HTML_URI = "http://www.w3.org/1999/html";
  @NonNls public static final String EMPTY_URI = "";

  // todo remove it
  @NonNls public static final Key<String> TEST_PATH = Key.create("TEST PATH");

  @NonNls public static final String TAGLIB_1_2_URI = "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd";
  @NonNls public static final String JSP_URI = "http://java.sun.com/JSP/Page";
  @NonNls public static final String JSTL_CORE_URI = "http://java.sun.com/jsp/jstl/core";
  @NonNls public static final String JSTL_CORE_URI2 = "http://java.sun.com/jstl/core";
  @NonNls public static final String JSTL_CORE_URI3 = "http://java.sun.com/jstl/core_rt";
  @NonNls public static final String JSTL_CORE_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsp/jstl/core";
  @NonNls public static final String[] JSTL_CORE_URIS = {JSTL_CORE_URI, JSTL_CORE_URI2, JSTL_CORE_URI3, JSTL_CORE_URI_JAVAEE_7};
  @NonNls public static final String JSF_HTML_URI = "http://java.sun.com/jsf/html";
  @NonNls public static final String JSF_HTML_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/html";
  @NonNls public static final String[] JSF_HTML_URIS = {JSF_HTML_URI, JSF_HTML_URI_JAVAEE_7};
  @NonNls public static final String JSF_CORE_URI = "http://java.sun.com/jsf/core";
  @NonNls public static final String JSF_CORE_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/core";
  @NonNls public static final String[] JSF_CORE_URIS = {JSF_CORE_URI, JSF_CORE_URI_JAVAEE_7};
  @NonNls public static final String JSF_PASS_THROUGH_ATTR_URI_JAVAEE7 = "http://xmlns.jcp.org/jsf";
  @NonNls public static final String JSF_PASSTHROUGH_URI = "http://xmlns.jcp.org/jsf/passthrough";
  @NonNls public static final String JSTL_FORMAT_URI = "http://java.sun.com/jsp/jstl/fmt";
  @NonNls public static final String JSTL_FORMAT_URI2 = "http://java.sun.com/jstl/fmt";
  @NonNls public static final String SPRING_URI = "http://www.springframework.org/tags";
  @NonNls public static final String SPRING_FORMS_URI = "http://www.springframework.org/tags/form";
  @NonNls public static final String STRUTS_BEAN_URI = "http://struts.apache.org/tags-bean";
  @NonNls public static final String STRUTS_BEAN_URI2 = "http://jakarta.apache.org/struts/tags-bean";
  @NonNls public static final String APACHE_I18N_URI = "http://jakarta.apache.org/taglibs/i18n-1.0";
  @NonNls public static final String STRUTS_LOGIC_URI = "http://struts.apache.org/tags-logic";
  @NonNls public static final String STRUTS_HTML_URI = "http://struts.apache.org/tags-html";
  @NonNls public static final String STRUTS_HTML_URI2 = "http://jakarta.apache.org/struts/tags-html";
  @NonNls public static final String APACHE_TRINIDAD_URI = "http://myfaces.apache.org/trinidad";
  @NonNls public static final String APACHE_TRINIDAD_HTML_URI = "http://myfaces.apache.org/trinidad/html";
  @NonNls public static final String XSD_SIMPLE_CONTENT_TAG = "simpleContent";
  @NonNls public static final String NO_NAMESPACE_SCHEMA_LOCATION_ATT = "noNamespaceSchemaLocation";
  @NonNls public static final String SCHEMA_LOCATION_ATT = "schemaLocation";
  @NonNls public static final String[] WEB_XML_URIS =
    {"http://java.sun.com/xml/ns/j2ee", "http://java.sun.com/xml/ns/javaee", "http://xmlns.jcp.org/xml/ns/javaee", "http://java.sun.com/dtd/web-app_2_3.dtd",
      "http://java.sun.com/j2ee/dtds/web-app_2_2.dtd"};
  @NonNls public static final String FACELETS_URI = "http://java.sun.com/jsf/facelets";
  @NonNls public static final String FACELETS_URI_JAVAEE_7 = "http://xmlns.jcp.org/jsf/facelets";
  @NonNls public static final String[] FACELETS_URIS = {FACELETS_URI, FACELETS_URI_JAVAEE_7};
  @NonNls public static final String JSTL_FUNCTIONS_URI = "http://java.sun.com/jsp/jstl/functions";
  @NonNls public static final String JSTL_FUNCTIONS_URI2 = "http://java.sun.com/jstl/functions";
  @NonNls public static final String JSTL_FUNCTIONS_JAVAEE_7 = "http://xmlns.jcp.org/jsp/jstl/functions";
  @NonNls public static final String[] JSTL_FUNCTIONS_URIS = {JSTL_FUNCTIONS_URI, JSTL_FUNCTIONS_URI2};
  @NonNls public static final String JSTL_FN_FACELET_URI = "com.sun.facelets.tag.jstl.fn.JstlFnLibrary";
  @NonNls public static final String JSTL_CORE_FACELET_URI = "com.sun.facelets.tag.jstl.core.JstlCoreLibrary";
  @NonNls public static final String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";
  @NonNls public static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
  public static final List<String> ourSchemaUrisList = Arrays.asList(SCHEMA_URIS);
  public static final Key<Boolean> ANT_FILE_SIGN = new Key<>("FORCED ANT FILE");
  @NonNls public static final String TAG_DIR_NS_PREFIX = "urn:jsptagdir:";
  @NonNls public static final String VALUE_ATTR_NAME = "value";
  @NonNls public static final String ENUMERATION_TAG_NAME = "enumeration";
  @NonNls public static final String HTML4_LOOSE_URI = "http://www.w3.org/TR/html4/loose.dtd";
  @NonNls public static final String WSDL_SCHEMA_URI = "http://schemas.xmlsoap.org/wsdl/";
  public static final String XHTML4_SCHEMA_LOCATION;
  public final static ThreadLocal<Boolean> BUILDING_DOM_STUBS = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.XmlUtil");
  @NonNls private static final String JSTL_FORMAT_URI3 = "http://java.sun.com/jstl/fmt_rt";
  @NonNls public static final String[] JSTL_FORMAT_URIS = {JSTL_FORMAT_URI, JSTL_FORMAT_URI2, JSTL_FORMAT_URI3};
  @NonNls private static final String FILE = "file:";
  @NonNls private static final String CLASSPATH = "classpath:/";
  @NonNls private static final String URN = "urn:";
  private final static Set<String> doNotVisitTags = new HashSet<>(Arrays.asList("annotation", "element", "attribute"));

  private XmlUtil() {
  }
  static {
    final URL xhtml4SchemaLocationUrl = XmlUtil.class.getResource(ExternalResourceManagerEx.STANDARD_SCHEMAS + "xhtml1-transitional.xsd");
    XHTML4_SCHEMA_LOCATION = VfsUtilCore.urlToPath(VfsUtilCore.toIdeaUrl(FileUtil.unquote(xhtml4SchemaLocationUrl.toExternalForm()), false));
  }

  public static String getSchemaLocation(XmlTag tag, final String namespace) {
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

  @Nullable
  public static String findNamespacePrefixByURI(XmlFile file, @NotNull @NonNls String uri) {
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

  public static String[] findNamespacesByURI(XmlFile file, String uri) {
    if (file == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlDocument document = file.getDocument();
    if (document == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final XmlTag tag = document.getRootTag();
    if (tag == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    XmlAttribute[] attributes = tag.getAttributes();


    List<String> result = new ArrayList<>();

    for (XmlAttribute attribute : attributes) {
      if (attribute.getName().startsWith("xmlns:") && attribute.getValue().equals(uri)) {
        result.add(attribute.getName().substring("xmlns:".length()));
      }
      if ("xmlns".equals(attribute.getName()) && attribute.getValue().equals(uri)) result.add("");
    }

    return ArrayUtil.toStringArray(result);
  }

  @Nullable
  public static String getXsiNamespace(XmlFile file) {
    return findNamespacePrefixByURI(file, XML_SCHEMA_INSTANCE_URI);
  }

  @Nullable
  public static XmlFile findNamespace(@NotNull PsiFile base, @NotNull String nsLocation) {
    final String location = ExternalResourceManager.getInstance().getResourceLocation(nsLocation, base.getProject());
    if (!location.equals(nsLocation)) { // is mapped
      return findXmlFile(base, location);
    }
    final XmlFile xmlFile = XmlSchemaProvider.findSchema(location, base);
    return xmlFile == null ? findXmlFile(base, location) : xmlFile;
  }

  @Nullable
  public static XmlFile findNamespaceByLocation(@NotNull PsiFile base, @NotNull String nsLocation) {
    final String location = ExternalResourceManager.getInstance().getResourceLocation(nsLocation, base.getProject());
    return findXmlFile(base, location);
  }

  public static Collection<XmlFile> findNSFilesByURI(String namespace, final Project project, Module module) {
    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      resources = XmlNamespaceIndex.getResourcesByNamespace(namespace, project, module);
    final PsiManager psiManager = PsiManager.getInstance(project);
    return ContainerUtil.mapNotNull(resources,
                                    (NullableFunction<IndexedRelevantResource<String, XsdNamespaceBuilder>, XmlFile>)resource -> {
                                      PsiFile file = psiManager.findFile(resource.getFile());
                                      return file instanceof XmlFile ? (XmlFile)file : null;
                                    });
  }

  @Nullable
  public static XmlFile findXmlFile(PsiFile base, @NotNull String uri) {
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

  @Nullable
  public static XmlToken getTokenOfType(PsiElement element, IElementType type) {
    if (element == null) {
      return null;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
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
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag);
  }

  public static boolean processXmlElements(XmlElement element, PsiElementProcessor processor, boolean deepFlag, boolean wideFlag) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag, baseFile);
  }

  public static boolean processXmlElements(final XmlElement element,
                                           final PsiElementProcessor processor,
                                           final boolean deepFlag,
                                           final boolean wideFlag,
                                           final PsiFile baseFile,
                                           boolean processIncludes) {
    return XmlPsiUtil.processXmlElements(element, processor, deepFlag, wideFlag, baseFile, processIncludes);
  }

  public static boolean processXmlElementChildren(final XmlElement element, final PsiElementProcessor processor, final boolean deepFlag) {
    return XmlPsiUtil.processXmlElementChildren(element, processor, deepFlag);
  }

  public static boolean tagFromTemplateFramework(@NotNull final XmlTag tag) {
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
        return XmlTagUtil.getCharacterByEntityName(text);
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

  public static boolean attributeFromTemplateFramework(@NonNls final String name, final XmlTag tag) {
    return "jsfc".equals(name) && isJsfHtmlScheme(tag);
  }

  @Nullable
  public static String getTargetSchemaNsFromTag(@Nullable final XmlTag xmlTag) {
    if (xmlTag == null) return null;
    String targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI2);
    if (targetNamespace == null) targetNamespace = xmlTag.getAttributeValue(TARGET_NAMESPACE_ATTR_NAME, XML_SCHEMA_URI3);
    return targetNamespace;
  }

  @Nullable
  public static XmlTag getSchemaSimpleContent(@NotNull XmlTag tag) {
    XmlElementDescriptor descriptor = tag.getDescriptor();

    if (descriptor instanceof XmlElementDescriptorImpl) {
      final TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType(tag);

      if (type instanceof ComplexTypeDescriptor) {
        final XmlTag[] simpleContent = new XmlTag[1];

        processXmlElements(((ComplexTypeDescriptor)type).getDeclaration(), new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull final PsiElement element) {
            if (element instanceof XmlTag) {
              final XmlTag tag = (XmlTag)element;
              @NonNls final String s = ((XmlTag)element).getLocalName();

              if ((s.equals(XSD_SIMPLE_CONTENT_TAG) ||
                   s.equals("restriction") && "string".equals(findLocalNameByQualifiedName(tag.getAttributeValue("base")))) &&
                  tag.getNamespace().equals(XML_SCHEMA_URI)) {
                simpleContent[0] = tag;
                return false;
              }
            }

            return true;
          }
        }, true);

        return simpleContent[0];
      }
    }
    return null;
  }

  public static <T extends PsiElement> void doDuplicationCheckForElements(final T[] elements,
                                                                          final Map<String, T> presentNames,
                                                                          DuplicationInfoProvider<T> provider,
                                                                          final Validator.ValidationHost host) {
    for (T t : elements) {
      final String name = provider.getName(t);
      if (name == null) continue;

      final String nameKey = provider.getNameKey(t, name);

      if (presentNames.containsKey(nameKey)) {
        final T psiElement = presentNames.get(nameKey);
        final String message = XmlErrorMessages.message("duplicate.declaration", nameKey);

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

  public static String getEntityValue(final XmlEntityRef entityRef) {
    final XmlEntityDecl decl = entityRef.resolve(entityRef.getContainingFile());
    if (decl != null) {
      final XmlAttributeValue valueElement = decl.getValueElement();
      if (valueElement != null) {
        final String value = valueElement.getValue();
        if (value != null) {
          return value;
        }
      }
    }
    return entityRef.getText();
  }

  public static boolean isAntFile(final PsiFile file) {
    if (file instanceof XmlFile) {
      final XmlFile xmlFile = (XmlFile)file;
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

  @Nullable
  public static PsiFile findRelativeFile(String uri, PsiElement base) {
    if (base instanceof PsiFile) {
      PsiFile baseFile = (PsiFile)base;
      VirtualFile file = UriUtil.findRelative(uri, baseFile.getOriginalFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }
    else if (base instanceof PsiDirectory) {
      PsiDirectory baseDir = (PsiDirectory)base;
      VirtualFile file = UriUtil.findRelative(uri, baseDir);
      if (file == null) return null;
      return base.getManager().findFile(file);
    }

    return null;
  }

  @Nullable
  public static String getCommentText(XmlComment comment) {
    final PsiElement firstChild = comment.getFirstChild();
    if (firstChild != null) {
      final PsiElement nextSibling = firstChild.getNextSibling();
      if (nextSibling instanceof XmlToken) {
        final XmlToken token = (XmlToken)nextSibling;
        if (token.getTokenType() == XmlTokenType.XML_COMMENT_CHARACTERS) {
          return token.getText();
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement findNamespaceDeclaration(XmlElement xmlElement, String nsName) {
    while (!(xmlElement instanceof XmlTag) && xmlElement != null) {
      final PsiElement parent = xmlElement.getParent();
      if (!(parent instanceof XmlElement)) return null;
      xmlElement = (XmlElement)parent;
    }
    if (xmlElement != null) {
      XmlTag tag = (XmlTag)xmlElement;
      while (tag != null) {
        for (XmlAttribute attribute : tag.getAttributes()) {
          if (attribute.isNamespaceDeclaration() && attribute.getLocalName().equals(nsName)) {
            return attribute;
          }
        }
        tag = tag.getParentTag();
      }
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

  @Nullable
  public static XmlElementDescriptor getDescriptorFromContext(@NotNull XmlTag tag) {
    PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)parent;
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
    if (!(node instanceof CompositeElement)) return;
    CompositeElement compositeElement = (CompositeElement)node;

    final LeafElement emptyTagEnd = (LeafElement)XmlChildRole.EMPTY_TAG_END_FINDER.findChild(compositeElement);
    if (emptyTagEnd == null) return;

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

  /**
   * @see XmlTag#getAttributeValue(String)
   */
  @Nullable
  @Deprecated
  public static String getAttributeValue(XmlTag tag, String name) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (name.equals(attribute.getName())) return attribute.getValue();
    }
    return null;
  }

  // Read the function name and parameter names to find out what this function does... :-)
  @Nullable
  public static XmlTag find(String subTag, String withValue, String forTag, XmlTag insideRoot) {
    final XmlTag[] forTags = insideRoot.findSubTags(forTag);

    for (XmlTag tag : forTags) {
      final XmlTag[] allTags = tag.findSubTags(subTag);

      for (XmlTag curTag : allTags) {
        if (curTag.getName().equals(subTag) && curTag.getValue().getTrimmedText().equalsIgnoreCase(withValue)) {
          return tag;
        }
      }
    }

    return null;
  }

  @Nullable
  @NonNls
  public static String[][] getDefaultNamespaces(final XmlDocument document) {
    final XmlFile file = getContainingFile(document);

    final XmlTag tag = document.getRootTag();
    if (tag == null) return null;

    @NotNull final XmlFileNSInfoProvider[] nsProviders = Extensions.getExtensions(XmlFileNSInfoProvider.EP_NAME);
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

  @Nullable
  public static String getDtdUri(XmlDocument document) {
    XmlProlog prolog = document.getProlog();
    if (prolog != null) {
      return getDtdUri(prolog.getDoctype());
    }
    return null;
  }

  @Nullable
  public static String getDtdUri(XmlDoctype doctype) {
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
      } else {
        if (id.contains("Strict")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd";
        } else if (id.contains("Frameset")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd";
        } else if (id.contains("Transitional")) {
          return "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd";
        }
      }
    } else if (id.contains("HTML")) {
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
      ContainerUtil.sort(list);
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

  @Nullable
  public static XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag) {
    return findXmlDescriptorByType(xmlTag, null);
  }

  @Nullable
  public static XmlElementDescriptor findXmlDescriptorByType(final XmlTag xmlTag, @Nullable XmlTag context) {
    String type = xmlTag.getAttributeValue("type", XML_SCHEMA_INSTANCE_URI);

    if (type == null) {
      String ns = xmlTag.getNamespace();
      if (ourSchemaUrisList.indexOf(ns) >= 0) {
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

      if (typeDecr instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl schemaDescriptor = (XmlNSDescriptorImpl)typeDecr;
        elementDescriptor = schemaDescriptor.getDescriptorByType(type, xmlTag);
      }
    }

    return elementDescriptor;
  }

  public static boolean collectEnumerationValues(final XmlTag element, final HashSet<String> variants) {
    return processEnumerationValues(element, xmlTag -> {
      variants.add(xmlTag.getAttributeValue(VALUE_ATTR_NAME));
      return true;
    });
  }

  /**
   * @return true if enumeration is exhaustive
   */
  public static boolean processEnumerationValues(final XmlTag element, final Processor<XmlTag> tagProcessor) {
    boolean exhaustiveEnum = true;

    for (final XmlTag tag : element.getSubTags()) {
      @NonNls final String localName = tag.getLocalName();

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
        processEnumerationValues(tag, tagProcessor);
      }
      else if (!doNotVisitTags.contains(localName)) {
        // don't go into annotation
        exhaustiveEnum &= processEnumerationValues(tag, tagProcessor);
      }
    }
    return exhaustiveEnum;
  }

  /**
   * @param xmlTag
   * @param localName
   * @param namespace
   * @param bodyText              pass null to create collapsed tag, empty string means creating expanded one
   * @param enforceNamespacesDeep
   * @return
   */
  public static XmlTag createChildTag(final XmlTag xmlTag,
                                      String localName,
                                      String namespace,
                                      @Nullable String bodyText,
                                      boolean enforceNamespacesDeep) {
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
      Language language = xmlTag.getLanguage();
      if (!(language instanceof HTMLLanguage)) language = XMLLanguage.INSTANCE;
      XmlTag retTag;
      if (bodyText != null) {
        retTag = XmlElementFactory.getInstance(xmlTag.getProject())
          .createTagFromText("<" + tagStart + ">" + bodyText + "</" + qname + ">", language);
        if (enforceNamespacesDeep) {
          retTag.acceptChildren(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
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
        retTag = XmlElementFactory.getInstance(xmlTag.getProject()).createTagFromText("<" + tagStart + "/>", language);
      }
      return retTag;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
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

    return Pair.create(first, last);
  }

  public static boolean isSimpleValue(@NotNull final String unquotedValue, final PsiElement context) {
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

  public static boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static boolean toCode(char ch) {
    return "<&>\u00a0".indexOf(ch) >= 0;
  }

  @Nullable
  public static PsiNamedElement findRealNamedElement(@NotNull final PsiNamedElement _element) {
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

      processXmlElements((XmlFile)currentElement, new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
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
        }
      }, true);

      return result[0];
    }

    return null;
  }

  public static int getPrefixLength(@NotNull final String s) {
    if (s.startsWith(TAG_DIR_NS_PREFIX)) return TAG_DIR_NS_PREFIX.length();
    if (s.startsWith(FILE)) return FILE.length();
    if (s.startsWith(CLASSPATH)) return CLASSPATH.length();
    return 0;
  }

  public static boolean isUrlText(final String s, Project project) {
    final boolean surelyUrl = HtmlUtil.hasHtmlPrefix(s) || s.startsWith(URN);
    if (surelyUrl) return surelyUrl;
    int protocolIndex = s.indexOf(":/");
    if (protocolIndex > 1 && !s.regionMatches(0,"classpath",0,protocolIndex)) return true;
    return ExternalResourceManager.getInstance().getResourceLocation(s, project) != s;
  }

  public static String generateDocumentDTD(XmlDocument doc, boolean full) {
    final Map<String, List<String>> tags = new LinkedHashMap<>();
    final Map<String, List<MyAttributeInfo>> attributes = new LinkedHashMap<>();

    try {
      XmlEntityRefImpl.setNoEntityExpandOutOfDocument(doc, true);
      final XmlTag rootTag = doc.getRootTag();
      computeTag(rootTag, tags, attributes, full);

      // For supporting not well-formed XML
      for (PsiElement element = rootTag != null ? rootTag.getNextSibling() : null; element != null; element = element.getNextSibling()) {
        if (element instanceof XmlTag) {
          computeTag((XmlTag)element, tags, attributes, full);
        }
      }
    }
    finally {
      XmlEntityRefImpl.setNoEntityExpandOutOfDocument(doc, false);
    }

    final StringBuilder buffer = new StringBuilder();
    for (final String tagName : tags.keySet()) {
      buffer.append(generateElementDTD(tagName, tags.get(tagName), attributes.get(tagName)));
    }
    return buffer.toString();
  }

  public static String generateElementDTD(String name, List<String> tags, List<MyAttributeInfo> attributes) {
    if (name == null || "".equals(name)) return "";
    if (name.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) return "";

    @NonNls final StringBuilder buffer = new StringBuilder();
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

  @Nullable
  public static String trimLeadingSpacesInMultilineTagValue(@NonNls String tagValue) {
    return tagValue == null ? null : tagValue.replaceAll("\n\\s*", "\n");
  }

  public static String findNamespaceByPrefix(final String prefix, XmlTag contextTag) {
    return contextTag.getNamespaceByPrefix(prefix);
  }

  @NotNull
  public static String findPrefixByQualifiedName(@NotNull String name) {
    final int prefixEnd = name.indexOf(':');
    if (prefixEnd > 0) {
      return name.substring(0, prefixEnd);
    }
    return "";
  }

  @Nullable
  public static String findLocalNameByQualifiedName(String name) {
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

  @Nullable
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

  public static String decode(@NonNls String text) {
    if (text.isEmpty()) return text;
    if (text.charAt(0) != '&' || text.length() < 3) {
      if (text.indexOf('<') < 0 && text.indexOf('>') < 0) return text;
      return text.replaceAll("<!\\[CDATA\\[", "").replaceAll("\\]\\]>", "");
    }

    if (text.equals("&lt;")) {
      return "<";
    }
    if (text.equals("&gt;")) {
      return ">";
    }
    if (text.equals("&nbsp;")) {
      return "\u00a0";
    }
    if (text.equals("&amp;")) {
      return "&";
    }
    if (text.equals("&apos;")) {
      return "'";
    }
    if (text.equals("&quot;")) {
      return "\"";
    }
    if (text.startsWith("&quot;") && text.endsWith("&quot;")) {
      return "\"" + text.substring(6, text.length() - 6) + "\"";
    }
    if (text.startsWith("&#")) {
      text = text.substring(3, text.length() - 1);
      try {
        return String.valueOf((char)Integer.parseInt(text));
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }

    return text;
  }

  public static String unescape(String text) {
    return StringUtil.unescapeXml(text);
  }

  public static String escape(String text) {
    return StringUtil.escapeXml(text);
  }

  public static boolean isValidTagNameChar(char c) {
    return Character.isLetter(c) || Character.isDigit(c) ||
           c == ':' || c == '_' || c == '-' || c == '.';
  }

  @Nullable
  public static String extractXmlEncodingFromProlog(@NotNull byte[] content) {
    return XmlCharsetDetector.extractXmlEncodingFromProlog(content);
  }

  @Nullable
  public static String extractXmlEncodingFromProlog(@NotNull CharSequence text) {
    return XmlCharsetDetector.extractXmlEncodingFromProlog(text);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @Nullable @NonNls String[] attributeNames,
                                                                @Nullable ElementFilter elementFilter,
                                                                @NotNull PsiReferenceProvider provider) {
    registerXmlAttributeValueReferenceProvider(registrar, attributeNames, elementFilter, true, provider);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @Nullable @NonNls String[] attributeNames,
                                                                @Nullable ElementFilter elementFilter,
                                                                boolean caseSensitive,
                                                                @NotNull PsiReferenceProvider provider) {
    registerXmlAttributeValueReferenceProvider(registrar, attributeNames, elementFilter, caseSensitive, provider,
                                               PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  public static void registerXmlAttributeValueReferenceProvider(PsiReferenceRegistrar registrar,
                                                                @Nullable @NonNls String[] attributeNames,
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
    String myName = null;

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
