// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.XmlTypedHandlersAdditionalSupport;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharsetDetector;
import com.intellij.util.ThreeState;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.*;

import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public final class HtmlUtil {
  private static final Logger LOG = Logger.getInstance(HtmlUtil.class);

  private static final @NonNls String JSFC = "jsfc";
  public static final @NonNls String HTML5_DATA_ATTR_PREFIX = BasicHtmlUtil.HTML5_DATA_ATTR_PREFIX;

  public static final @NlsSafe String SCRIPT_TAG_NAME = BasicHtmlUtil.SCRIPT_TAG_NAME;
  public static final @NlsSafe String STYLE_TAG_NAME = BasicHtmlUtil.STYLE_TAG_NAME;
  public static final @NlsSafe String TEMPLATE_TAG_NAME = BasicHtmlUtil.TEMPLATE_TAG_NAME;
  public static final @NlsSafe String TEXTAREA_TAG_NAME = BasicHtmlUtil.TEXTAREA_TAG_NAME;
  public static final @NlsSafe String TITLE_TAG_NAME = BasicHtmlUtil.TITLE_TAG_NAME;
  public static final @NlsSafe String SLOT_TAG_NAME = BasicHtmlUtil.SLOT_TAG_NAME;

  public static final @NlsSafe String STYLE_ATTRIBUTE_NAME = BasicHtmlUtil.STYLE_ATTRIBUTE_NAME;
  public static final @NlsSafe String SRC_ATTRIBUTE_NAME = BasicHtmlUtil.SRC_ATTRIBUTE_NAME;
  public static final @NlsSafe String ID_ATTRIBUTE_NAME = BasicHtmlUtil.ID_ATTRIBUTE_NAME;
  public static final @NlsSafe String CLASS_ATTRIBUTE_NAME = BasicHtmlUtil.CLASS_ATTRIBUTE_NAME;
  public static final @NlsSafe String TYPE_ATTRIBUTE_NAME = BasicHtmlUtil.TYPE_ATTRIBUTE_NAME;
  public static final @NlsSafe String LANGUAGE_ATTRIBUTE_NAME = BasicHtmlUtil.LANGUAGE_ATTRIBUTE_NAME;
  public static final @NlsSafe String LANG_ATTRIBUTE_NAME = BasicHtmlUtil.LANG_ATTRIBUTE_NAME;

  public static final @NonNls String MATH_ML_NAMESPACE = BasicHtmlUtil.MATH_ML_NAMESPACE;
  public static final @NonNls String SVG_NAMESPACE = BasicHtmlUtil.SVG_NAMESPACE;

  public static final String[] RFC2616_HEADERS = BasicHtmlUtil.RFC2616_HEADERS;

  private HtmlUtil() { }

  public static boolean isSingleHtmlTag(@NotNull XmlTag tag, boolean toLowerCase) {
    final String name = tag.getName();
    boolean result = BasicHtmlUtil.EMPTY_TAGS_MAP.contains(!toLowerCase || tag.isCaseSensitive()
                                             ? name : StringUtil.toLowerCase(name));
    return result && !XmlCustomElementDescriptor.isCustomElement(tag);
  }

  public static boolean isSingleHtmlTag(String tagName, boolean caseSensitive) {
    return BasicHtmlUtil.isSingleHtmlTag(tagName, caseSensitive);
  }

  public static boolean isTagWithOptionalEnd(@NotNull String tagName, boolean caseSensitive) {
    return BasicHtmlUtil.isTagWithOptionalEnd(tagName, caseSensitive);
  }

  public static @NotNull ThreeState canOpeningTagAutoClose(@NotNull String tagToClose,
                                                           @NotNull String openingTag,
                                                           boolean caseSensitive) {
    return BasicHtmlUtil.canOpeningTagAutoClose(tagToClose, openingTag, caseSensitive);
  }

  public static boolean canClosingTagAutoClose(@NotNull String tagToClose,
                                               @NotNull String closingTag,
                                               boolean caseSensitive) {
    return BasicHtmlUtil.canClosingTagAutoClose(tagToClose, closingTag, caseSensitive);
  }

  public static boolean isHtmlBlockTag(String tagName, boolean caseSensitive) {
    return BasicHtmlUtil.isHtmlBlockTag(tagName, caseSensitive);
  }

  public static boolean isPossiblyInlineTag(@NotNull String tagName) {
    return BasicHtmlUtil.isPossiblyInlineTag(tagName);
  }

  public static boolean isInlineTagContainer(String tagName, boolean caseSensitive) {
    return BasicHtmlUtil.isInlineTagContainer(tagName, caseSensitive);
  }

  public static void addHtmlSpecificCompletions(final XmlElementDescriptor descriptor,
                                                final XmlTag element,
                                                final List<? super XmlElementDescriptor> variants) {
    // add html block completions for tags with optional ends!
    String name = descriptor.getName(element);

    if (name != null && isTagWithOptionalEnd(name, false)) {
      PsiElement parent = element.getParent();

      if (parent instanceof XmlTag && XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parent.getNode()) != null) {
        return;
      }

      if (parent != null) {
        // we need grand parent since completion already uses parent's descriptor
        parent = parent.getParent();
      }

      if (parent instanceof HtmlTag) {
        final XmlElementDescriptor parentDescriptor = ((HtmlTag)parent).getDescriptor();

        if (parentDescriptor != descriptor && parentDescriptor != null) {
          for (final XmlElementDescriptor elementsDescriptor : parentDescriptor.getElementsDescriptors((XmlTag)parent)) {
            if (isHtmlBlockTag(elementsDescriptor.getName(), false)) {
              variants.add(elementsDescriptor);
            }
          }
        }
      }
      else if (parent instanceof HtmlDocumentImpl) {
        final XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
        if (nsDescriptor != null) {
          for (XmlElementDescriptor elementDescriptor : nsDescriptor.getRootElementsDescriptors((XmlDocument)parent)) {
            if (isHtmlBlockTag(elementDescriptor.getName(), false) && !variants.contains(elementDescriptor)) {
              variants.add(elementDescriptor);
            }
          }
        }
      }
    }
  }

  public static @Nullable XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
    return HtmlPsiUtil.getRealXmlDocument(doc);
  }

  public static boolean isShortNotationOfBooleanAttributePreferred() {
    return Registry.is("html.prefer.short.notation.of.boolean.attributes", true);
  }

  @TestOnly
  public static void setShortNotationOfBooleanAttributeIsPreferred(boolean value, Disposable parent) {
    final boolean oldValue = isShortNotationOfBooleanAttributePreferred();
    final RegistryValue registryValue = Registry.get("html.prefer.short.notation.of.boolean.attributes");
    registryValue.setValue(value);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        registryValue.setValue(oldValue);
      }
    });
  }

  public static boolean isBooleanAttribute(@NotNull XmlAttributeDescriptor descriptor, @Nullable PsiElement context) {
    if (descriptor.isEnumerated()) {
      final String[] values = descriptor.getEnumeratedValues();
      if (values == null) {
        return false;
      }
      if (values.length == 2) {
        return values[0].isEmpty() && values[1].equals(descriptor.getName())
               || values[1].isEmpty() && values[0].equals(descriptor.getName());
      }
      else if (values.length == 1) {
        return descriptor.getName().equals(values[0]);
      }
    }
    return context != null && isCustomBooleanAttribute(descriptor.getName(), context);
  }

  public static boolean isCustomBooleanAttribute(@NotNull String attributeName, @NotNull PsiElement context) {
    final String entitiesString = getEntitiesString(context, XmlEntitiesInspection.BOOLEAN_ATTRIBUTE_SHORT_NAME);
    if (entitiesString != null) {
      StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
      while (tokenizer.hasMoreElements()) {
        if (tokenizer.nextToken().equalsIgnoreCase(attributeName)) {
          return true;
        }
      }
    }
    return false;
  }

  public static XmlAttributeDescriptor[] getCustomAttributeDescriptors(PsiElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.ATTRIBUTE_SHORT_NAME);
    if (entitiesString == null) return XmlAttributeDescriptor.EMPTY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlAttributeDescriptor[] descriptors = new XmlAttributeDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String customName = tokenizer.nextToken();
      if (customName.isEmpty()) continue;

      descriptors[index++] = new XmlAttributeDescriptorImpl() {
        @Override
        public String getName(PsiElement context) {
          return customName;
        }

        @Override
        public String getName() {
          return customName;
        }
      };
    }

    return descriptors;
  }

  public static XmlElementDescriptor[] getCustomTagDescriptors(@Nullable PsiElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.TAG_SHORT_NAME);
    if (entitiesString == null) return XmlElementDescriptor.EMPTY_ARRAY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlElementDescriptor[] descriptors = new XmlElementDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String tagName = tokenizer.nextToken();
      if (tagName.isEmpty()) continue;

      descriptors[index++] = new CustomXmlTagDescriptor(tagName);
    }

    return descriptors;
  }

  public static @Nullable String getEntitiesString(@Nullable PsiElement context, @NotNull String inspectionName) {
    if (context == null) return null;
    var containingFile = context.getContainingFile();
    if (containingFile == null) return null;
    PsiFile originalFile = containingFile.getOriginalFile();

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
    XmlEntitiesInspection inspection = (XmlEntitiesInspection)profile.getUnwrappedTool(inspectionName, originalFile);
    if (inspection != null) {
      return inspection.getAdditionalEntries();
    }
    return null;
  }

  public static XmlAttributeDescriptor[] appendHtmlSpecificAttributeCompletions(final XmlTag declarationTag,
                                                                                XmlAttributeDescriptor[] descriptors,
                                                                                final XmlAttribute context) {
    if (declarationTag instanceof HtmlTag) {
      descriptors = ArrayUtil.mergeArrays(
        descriptors,
        getCustomAttributeDescriptors(context)
      );
      return descriptors;
    }


    boolean isJsfHtmlNamespace = false;
    for (String jsfHtmlUri : XmlUtil.JSF_HTML_URIS) {
      if (declarationTag.getPrefixByNamespace(jsfHtmlUri) != null) {
        isJsfHtmlNamespace = true;
        break;
      }
    }

    if (isJsfHtmlNamespace && declarationTag.getNSDescriptor(XmlUtil.XHTML_URI, true) != null &&
        !XmlUtil.JSP_URI.equals(declarationTag.getNamespace())) {

      descriptors = ArrayUtil.append(
        descriptors,
        new XmlAttributeDescriptorImpl() {
          @Override
          public String getName(PsiElement context) {
            return JSFC;
          }

          @Override
          public String getName() {
            return JSFC;
          }
        },
        XmlAttributeDescriptor.class
      );
    }
    return descriptors;
  }

  public static boolean isHtml5Document(XmlDocument doc) {
    if (doc == null) {
      return false;
    }
    XmlProlog prolog = doc.getProlog();
    XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;

    final PsiFile htmlFile = doc.getContainingFile();

    final String htmlFileFullName;
    if (htmlFile != null) {
      final VirtualFile vFile = htmlFile.getVirtualFile();
      htmlFileFullName = vFile == null ? htmlFile.getName() : vFile.getPath();
    }
    else {
      htmlFileFullName = "unknown";
    }

    if (doctype == null) {
      LOG.debug("DOCTYPE for " + htmlFileFullName + " is null");
      return isHtmlTagContainingFile(doc) && Html5SchemaProvider.getHtml5SchemaLocation()
        .equals(ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(doc.getProject()));
    }

    final boolean html5Doctype = isHtml5Doctype(doctype);
    final String doctypeDescription = "text: " + doctype.getText() +
                                      ", dtdUri: " + doctype.getDtdUri() +
                                      ", publicId: " + doctype.getPublicId() +
                                      ", markupDecl: " + doctype.getMarkupDecl();
    LOG.debug("DOCTYPE for " + htmlFileFullName + "; " + doctypeDescription + "; HTML5: " + html5Doctype);
    return html5Doctype;
  }

  public static boolean isHtml5Doctype(XmlDoctype doctype) {
    return doctype.getDtdUri() == null && doctype.getPublicId() == null && doctype.getMarkupDecl() == null;
  }

  public static boolean isHtml5Context(XmlElement context) {
    XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
    if (doc == null && context != null) {
      return Html5SchemaProvider.getHtml5SchemaLocation()
        .equals(ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(context.getProject()));
    }
    return isHtml5Document(doc);
  }

  public static boolean isHtmlTag(@NotNull XmlTag tag) {
    if (!tag.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) return false;

    XmlDocument doc = PsiTreeUtil.getParentOfType(tag, XmlDocument.class);

    String doctype = null;
    if (doc != null) {
      doctype = XmlUtil.getDtdUri(doc);
    }
    doctype = doctype == null ? ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(tag.getProject()) : doctype;
    return XmlUtil.XHTML4_SCHEMA_LOCATION.equals(doctype) ||
           !StringUtil.containsIgnoreCase(doctype, "xhtml");
  }

  public static boolean hasNonHtml5Doctype(XmlElement context) {
    XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
    if (doc == null) {
      return false;
    }
    XmlProlog prolog = doc.getProlog();
    XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;
    return doctype != null && !isHtml5Doctype(doctype);
  }

  public static boolean isHtml5Tag(@NotNull String tagName) {
    return BasicHtmlUtil.isHtml5Tag(tagName);
  }

  public static boolean isCustomHtml5Attribute(String attributeName) {
    return BasicHtmlUtil.isCustomHtml5Attribute(attributeName);
  }

  public static @Nullable String getHrefBase(XmlFile file) {
    final XmlTag root = file.getRootTag();
    final XmlTag head = root != null ? root.findFirstSubTag("head") : null;
    final XmlTag base = head != null ? head.findFirstSubTag("base") : null;
    return base != null ? base.getAttributeValue("href") : null;
  }

  public static boolean isOwnHtmlAttribute(XmlAttributeDescriptor descriptor) {
    // common html attributes are defined mostly in common.rnc, core-scripting.rnc, etc
    // while own tag attributes are defined in meta.rnc
    final PsiElement declaration = descriptor.getDeclaration();
    final PsiFile file = declaration != null ? declaration.getContainingFile() : null;
    final String name = file != null ? file.getName() : null;
    return "meta.rnc".equals(name) || "web-forms.rnc".equals(name)
           || "embed.rnc".equals(name) || "tables.rnc".equals(name)
           || "media.rnc".equals(name);
  }

  public static boolean tagHasHtml5Schema(@NotNull XmlTag context) {
    XmlElementDescriptor descriptor = context.getDescriptor();
    XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    return isHtml5Schema(nsDescriptor);
  }

  public static boolean isHtml5Schema(@Nullable XmlNSDescriptor nsDescriptor) {
    XmlFile descriptorFile = nsDescriptor != null ? nsDescriptor.getDescriptorFile() : null;
    String descriptorPath = descriptorFile != null ? descriptorFile.getVirtualFile().getPath() : null;
    return Objects.equals(Html5SchemaProvider.getHtml5SchemaLocation(), descriptorPath) ||
           Objects.equals(Html5SchemaProvider.getXhtml5SchemaLocation(), descriptorPath);
  }

  /**
   * Checks if the specified string starts with an HTML tag, and if it does, it returns the tag name.
   *
   * @param line the string to check if it starts with an HTML tag
   * @return if the input starts with an HTML tag, it returns the tag name, otherwise {@code null}
   */
  public static String getStartTag(@NotNull String line) {
    return BasicHtmlUtil.getStartTag(line);
  }

  public static boolean startsWithTag(@NotNull String line) {
    return BasicHtmlUtil.startsWithTag(line);
  }

  public static Charset detectCharsetFromMetaTag(@NotNull CharSequence content) {
    return CharsetDetector.detectCharsetFromMetaTag(content);
  }

  public static boolean isTagWithoutAttributes(@NonNls String tagName) {
    return BasicHtmlUtil.isTagWithoutAttributes(tagName);
  }

  public static boolean hasHtml(@NotNull PsiFile file) {
    return BasicHtmlUtil.hasHtml(file);
  }

  public static boolean supportsXmlTypedHandlers(@NotNull PsiFile file) {
    return XmlTypedHandlersAdditionalSupport.supportsTypedHandlers(file);
  }

  public static boolean hasHtmlPrefix(@NotNull String url) {
    return BasicHtmlUtil.hasHtmlPrefix(url);
  }

  public static boolean isHtmlFile(@NotNull PsiElement element) {
    return BasicHtmlUtil.isHtmlFile(element);
  }

  public static boolean isHtmlFile(@NotNull VirtualFile file) {
    var registry = FileTypeRegistry.getInstance();
    return registry.isFileOfType(file, HtmlFileType.INSTANCE) || registry.isFileOfType(file, XHtmlFileType.INSTANCE);
  }

  public static FileType @NotNull [] getHtmlFileTypes() {
    return new FileType[]{HtmlFileType.INSTANCE, XHtmlFileType.INSTANCE};
  }

  public static boolean isHtmlTagContainingFile(PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      if (containingFile instanceof HtmlCompatibleFile) {
        return true;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (tag instanceof HtmlTag) {
        return true;
      }
      final XmlDocument document = PsiTreeUtil.getParentOfType(element, XmlDocument.class, false);
      if (document instanceof HtmlDocumentImpl) {
        return true;
      }
      final FileViewProvider provider = containingFile.getViewProvider();
      Language language;
      if (provider instanceof TemplateLanguageFileViewProvider) {
        language = ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage();
      }
      else {
        language = provider.getBaseLanguage();
      }

      return language == XHTMLLanguage.INSTANCE;
    }
    return false;
  }

  public static boolean isScriptTag(@Nullable XmlTag tag) {
    return tag != null && tag.getLocalName().equalsIgnoreCase(SCRIPT_TAG_NAME);
  }

  public static class CustomXmlTagDescriptor extends XmlElementDescriptorImpl {
    private final String myTagName;

    CustomXmlTagDescriptor(String tagName) {
      super(null);
      myTagName = tagName;
    }

    @Override
    public String getName(PsiElement context) {
      return myTagName;
    }

    @Override
    public String getDefaultName() {
      return myTagName;
    }

    @Override
    public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
      return true;
    }
  }

  public static @NotNull Iterable<String> splitClassNames(@Nullable String classAttributeValue) {
    return BasicHtmlUtil.splitClassNames(classAttributeValue);
  }

  @Contract("!null -> !null")
  public static @NlsSafe String getTagPresentation(@Nullable XmlTag tag) {
    if (tag == null) return null;
    StringBuilder builder = new StringBuilder(tag.getLocalName());
    String idValue = getAttributeValue(tag, ID_ATTRIBUTE_NAME);
    if (idValue != null) {
      builder.append('#').append(idValue);
    }
    String classValue = getAttributeValue(tag, CLASS_ATTRIBUTE_NAME);
    if (classValue != null) {
      for (String className : splitClassNames(classValue)) {
        builder.append('.').append(className);
      }
    }
    return builder.toString();
  }

  private static @Nullable String getAttributeValue(@NotNull XmlTag tag, @NotNull String attrName) {
    XmlAttribute classAttribute = getAttributeByName(tag, attrName);
    if (classAttribute != null && !containsOuterLanguageElements(classAttribute)) {
      String value = classAttribute.getValue();
      if (!StringUtil.isEmptyOrSpaces(value)) return value;
    }
    return null;
  }

  private static @Nullable XmlAttribute getAttributeByName(@NotNull XmlTag tag, @NotNull String name) {
    PsiElement child = tag.getFirstChild();
    while (child != null) {
      if (child instanceof XmlAttribute) {
        PsiElement nameElement = child.getFirstChild();
        if (nameElement != null &&
            nameElement.getNode().getElementType() == XmlTokenType.XML_NAME &&
            name.equalsIgnoreCase(nameElement.getText())) {
          return (XmlAttribute)child;
        }
      }
      child = child.getNextSibling();
    }
    return null;
  }

  private static boolean containsOuterLanguageElements(@NotNull PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof CompositeElement) {
        return containsOuterLanguageElements(child);
      }
      if (child instanceof OuterLanguageElement) {
        return true;
      }
      child = child.getNextSibling();
    }
    return false;
  }

  public static List<XmlAttributeValue> getIncludedPathsElements(final @NotNull XmlFile file) {
    final List<XmlAttributeValue> result = new ArrayList<>();
    file.acceptChildren(new XmlRecursiveElementWalkingVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        XmlAttribute attribute = null;
        if ("link".equalsIgnoreCase(tag.getName())) {
          attribute = tag.getAttribute("href");
        }
        else if ("script".equalsIgnoreCase(tag.getName()) || "img".equalsIgnoreCase(tag.getName())) {
          attribute = tag.getAttribute("src");
        }
        if (attribute != null) result.add(attribute.getValueElement());
        super.visitXmlTag(tag);
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element.getLanguage() instanceof XMLLanguage) {
          super.visitElement(element);
        }
      }
    });
    return result.isEmpty() ? Collections.emptyList() : result;
  }
}
