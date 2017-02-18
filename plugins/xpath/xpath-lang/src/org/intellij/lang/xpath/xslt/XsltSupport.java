/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.SmartList;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import icons.XpathIcons;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XsltSupport {

  public static final String XALAN_EXTENSION_PREFIX = "http://xml.apache.org/xalan/";
  public static final String XSLT_NS = "http://www.w3.org/1999/XSL/Transform";
  public static final String PLUGIN_EXTENSIONS_NS = "urn:idea:xslt-plugin#extensions";
  public static final Key<ParameterizedCachedValue<XsltChecker.LanguageLevel, PsiFile>> FORCE_XSLT_KEY = Key.create("FORCE_XSLT");
  public static final TextAttributesKey XSLT_DIRECTIVE =
    TextAttributesKey.createTextAttributesKey("XSLT_DIRECTIVE", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);

  private static final Map<String, String> XPATH_ATTR_MAP = new THashMap<>(10);
  private static final Map<String, Set<String>> XPATH_AVT_MAP = new THashMap<>(10);

  static {
    XPATH_ATTR_MAP.put("select", "");
    XPATH_ATTR_MAP.put("match", "");
    XPATH_ATTR_MAP.put("test", "");
    XPATH_ATTR_MAP.put("use-when", "");
    XPATH_ATTR_MAP.put("group-by", "");
    XPATH_ATTR_MAP.put("group-adjacent", "");
    XPATH_ATTR_MAP.put("count", "number");
    XPATH_ATTR_MAP.put("from", "number");
    XPATH_ATTR_MAP.put("value", "number");
    XPATH_ATTR_MAP.put("use", "key");

    XPATH_AVT_MAP.put("element", new THashSet<>(Arrays.asList("name", "namespace")));
    XPATH_AVT_MAP.put("attribute", new THashSet<>(Arrays.asList("name", "namespace")));
    XPATH_AVT_MAP.put("namespace", new THashSet<>(Arrays.asList("name")));
    XPATH_AVT_MAP.put("processing-instruction", new THashSet<>(Arrays.asList("name")));

    XPATH_AVT_MAP.put("number", new THashSet<>(
      Arrays.asList("format", "lang", "letter-value", "grouping-separator", "grouping-size", "ordinal")));
    XPATH_AVT_MAP.put("sort", new THashSet<>(Arrays.asList("lang", "data-type", "order", "case-order", "collation")));

    XPATH_AVT_MAP.put("message", new THashSet<>(Arrays.asList("terminate")));
    XPATH_AVT_MAP.put("value-of", new THashSet<>(Arrays.asList("separator")));

    XPATH_AVT_MAP.put("result-document", new THashSet<>(Arrays.asList("format", "href", "method", "byte-order-mark",
                                                                      "cdata-section-elements", "doctype-public", "doctype-system",
                                                                      "encoding", "escape-uri-attributes", "include-content-type",
                                                                      "indent", "media-type", "normalization-form",
                                                                      "omit-xml-declaration", "standalone", "undeclare-prefixes",
                                                                      "output-version")));
  }

  private XsltSupport() {
  }

  @NotNull
  public static PsiFile[] getFiles(XmlAttribute attribute) {
    final XmlAttributeValue value = attribute.getValueElement();
    if (value != null) {
      final List<PsiFile> files = new SmartList<>();
      InjectedLanguageUtil.enumerate(value, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (injectedPsi instanceof XPathFile) {
            files.add(injectedPsi);
          }
        }
      });
      return files.isEmpty() ? PsiFile.EMPTY_ARRAY : PsiUtilCore.toPsiFileArray(files);
    }
    return PsiFile.EMPTY_ARRAY;
  }

  public static boolean isXsltAttribute(@NotNull XmlAttribute attribute) {
    return isXsltTag(attribute.getParent());
  }

  public static boolean isXsltTag(@NotNull XmlTag tag) {
    if (!tag.isValid()) return false;
    final String s = tag.getNamespace();
    return XSLT_NS.equals(s) || s.startsWith(XALAN_EXTENSION_PREFIX);
  }

  private static boolean isXsltCoreTag(@NotNull XmlTag tag) {
    return tag.isValid() && XSLT_NS.equals(tag.getNamespace());
  }

  public static boolean isXPathAttribute(@NotNull XmlAttribute attribute) {
    if (attribute.getValueElement() == null) return false;

    final String name = attribute.getName();
    if (isXsltAttribute(attribute)) {
      final String tagName = attribute.getParent().getLocalName();
      final String s = XPATH_ATTR_MAP.get(name);
      if ((s == null || s.length() > 0) && !tagName.equals(s)) {
        if (!isAttributeValueTemplate(attribute, true)) {
          return false;
        }
      }
    }
    else {
      if (!isAttributeValueTemplate(attribute, false)) {
        return false;
      }
    }

    final PsiFile file = attribute.getContainingFile();
    if (file != null) {
      XsltChecker.LanguageLevel level = getXsltLanguageLevel(file);
      if (level != XsltChecker.LanguageLevel.NONE) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAttributeValueTemplate(@NotNull XmlAttribute attribute, boolean isXsltAttribute) {
    return (!isXsltAttribute || mayBeAVT(attribute)) && getAVTOffset(attribute.getValue(), 0) != -1;
  }

  public static boolean isVariableOrParamName(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isVariableOrParam(attribute.getParent());
  }

  public static boolean isVariableOrParam(@NotNull XmlTag tag) {
    final String localName = tag.getLocalName();
    return ("variable".equals(localName) || "param".equals(localName)) && isXsltCoreTag(tag);
  }

  public static boolean isVariable(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isVariable(attribute.getParent());
  }

  public static boolean isVariable(@NotNull XmlTag tag) {
    final String localName = tag.getLocalName();
    return "variable".equals(localName) && isXsltCoreTag(tag);
  }

  public static boolean isParam(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isParam(attribute.getParent());
  }

  public static boolean isParam(@NotNull XmlTag tag) {
    final String localName = tag.getLocalName();
    return "param".equals(localName) && isXsltCoreTag(tag);
  }

  public static boolean isPatternAttribute(@NotNull XmlAttribute attribute) {
    if (!isXsltAttribute(attribute)) return false;

    final String name = attribute.getName();
    if ("match".equals(name)) {
      return true;
    }
    else if ("count".equals(name) || "from".equals(name)) {
      return "number".equals(attribute.getParent().getLocalName());
    }
    return false;
  }

  public static boolean isTemplateCall(@NotNull XmlTag tag) {
    return "call-template".equals(tag.getLocalName()) && hasNameAttribute(tag) && isXsltCoreTag(tag);
  }

  public static boolean isFunction(@NotNull XmlTag tag) {
    return "function".equals(tag.getLocalName()) && hasNameAttribute(tag) && isXsltCoreTag(tag);
  }

  public static boolean isApplyTemplates(@NotNull XmlTag tag) {
    final String localName = tag.getLocalName();
    return "apply-templates".equals(localName) && isXsltCoreTag(tag);
  }

  private static boolean hasNameAttribute(@NotNull XmlTag tag) {
    return tag.getAttribute("name", null) != null;
  }

  public static boolean isTemplateCallName(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isTemplateCall(attribute.getParent());
  }

  private static boolean isXsltNameAttribute(@NotNull XmlAttribute attribute) {
    return "name".equals(attribute.getName()) && isXsltAttribute(attribute);
  }

  public static boolean isTemplateName(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isTemplate(attribute.getParent());
  }

  public static boolean isFunctionName(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isFunction(attribute.getParent());
  }

  public static boolean isTemplate(@NotNull XmlTag element) {
    return isTemplate(element, true);
  }

  public static boolean isTemplate(@NotNull XmlTag element, boolean requireName) {
    return "template".equals(element.getLocalName()) && (!requireName || hasNameAttribute(element)) && isXsltCoreTag(element);
  }

  public static boolean isXsltFile(@NotNull PsiFile psiFile) {
    if (psiFile.getFileType() != StdFileTypes.XML) return false;

    if (!(psiFile instanceof XmlFile)) return false;

    final XsltChecker.LanguageLevel level = getXsltLanguageLevel(psiFile);
    return level != XsltChecker.LanguageLevel.NONE;
  }

  public static XsltChecker.LanguageLevel getXsltLanguageLevel(@NotNull PsiFile psiFile) {
    final CachedValuesManager mgr = CachedValuesManager.getManager(psiFile.getProject());
    return mgr.getParameterizedCachedValue(psiFile, FORCE_XSLT_KEY, XsltSupportProvider.INSTANCE, false, psiFile);
  }

  public static boolean isXsltRootTag(@NotNull XmlTag tag) {
    final String localName = tag.getLocalName();
    return ("stylesheet".equals(localName) || "transform".equals(localName)) && isXsltCoreTag(tag);
  }

  public static boolean isTemplateCallParamName(@NotNull XmlAttribute attribute) {
    return isXsltNameAttribute(attribute) && isTemplateCallParam(attribute.getParent());
  }

  private static boolean isTemplateCallParam(@NotNull XmlTag parent) {
    return "with-param".equals(parent.getLocalName()) && hasNameAttribute(parent) && isXsltCoreTag(parent);
  }

  public static boolean isTopLevelElement(XmlTag tag) {
    XmlTag p = tag;
    // not really necessary, XSLT doesn't allow literal result elements on top level anyway
    while ((p = p.getParentTag()) != null) {
      if (isXsltTag(p)) {
        return isXsltRootTag(p);
      }
    }
    return false;
  }

  public static boolean isIncludeOrImportHref(XmlAttribute xmlattribute) {
    if (xmlattribute == null || !isXsltAttribute(xmlattribute)) return false;
    final String localName = xmlattribute.getParent().getLocalName();
    return isIncludeOrImport(localName) && "href".equals(xmlattribute.getName());
  }

  private static boolean isIncludeOrImport(String localName) {
    // treat import and include the same. right now it doesn't seem necessary to distinguish them
    return ("import".equals(localName) || "include".equals(localName));
  }

  public static boolean isIncludeOrImport(XmlTag tag) {
    if (tag == null) return false;
    return isIncludeOrImport(tag.getLocalName()) && isXsltCoreTag(tag) && tag.getAttribute("href", null) != null;
  }

  public static boolean isImport(XmlTag tag) {
    if (tag == null) return false;
    return "import".equals(tag.getLocalName()) && isXsltCoreTag(tag) && tag.getAttribute("href", null) != null;
  }

  @Nullable
  public static PsiElement getAttValueToken(@NotNull XmlAttribute attribute) {
    final XmlAttributeValue valueElement = attribute.getValueElement();
    if (valueElement != null) {
      final PsiElement firstChild = valueElement.getFirstChild();
      if (firstChild != null) {
        final PsiElement nextSibling = firstChild.getNextSibling();
        return nextSibling instanceof XmlToken && ((XmlToken)nextSibling).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
               ? nextSibling
               : null;
      }
    }
    return null;
  }

  public static boolean isMode(XmlAttribute xmlattribute) {
    if ("mode".equals(xmlattribute.getName())) {
      final XmlTag parent = xmlattribute.getParent();
      return isApplyTemplates(parent) || isTemplate(parent, false);
    }
    return false;
  }

  public static int getAVTOffset(String value, int i) {
    do {
      i = value.indexOf('{', i);
      if (i != -1 && i == value.indexOf("{{", i)) {
        i += 2;
      }
      else {
        break;
      }
    }
    while (i != -1);
    return i;
  }

  public static boolean mayBeAVT(@NotNull XmlAttribute attribute) {
    if (isXsltAttribute(attribute)) {
      final String tagName = attribute.getParent().getLocalName();

      final Set<String> allowedAttrs = XPATH_AVT_MAP.get(tagName);
      if (allowedAttrs == null) return isExtensionAvtAttribute(attribute);

      return allowedAttrs.contains(attribute.getName());
    }
    else {
      return true;
    }
  }

  private static boolean isExtensionAvtAttribute(XmlAttribute attribute) {
    final String namespace = attribute.getParent().getNamespace();
    return namespace.startsWith(XALAN_EXTENSION_PREFIX) && "file".equals(attribute.getName());
  }

  public static Icon createXsltIcon(Icon icon) {
    return LayeredIcon.create(icon, XpathIcons.Xslt_filetype_overlay);
  }

  private static class XsltSupportProvider implements ParameterizedCachedValueProvider<XsltChecker.LanguageLevel, PsiFile> {
    public static final ParameterizedCachedValueProvider<XsltChecker.LanguageLevel, PsiFile> INSTANCE = new XsltSupportProvider();

    public CachedValueProvider.Result<XsltChecker.LanguageLevel> compute(PsiFile psiFile) {
      if (!(psiFile instanceof XmlFile)) {
        return CachedValueProvider.Result.create(XsltChecker.LanguageLevel.NONE, PsiModificationTracker.MODIFICATION_COUNT);
      }

      final XmlFile xmlFile = (XmlFile)psiFile;
      if (psiFile instanceof PsiFileEx) {
        if (((PsiFileEx)psiFile).isContentsLoaded()) {
          final XmlDocument doc = xmlFile.getDocument();
          if (doc != null) {
            final XmlTag rootTag = doc.getRootTag();
            if (rootTag != null) {
              XmlAttribute v;
              XsltChecker.LanguageLevel level;
              if (isXsltRootTag(rootTag)) {
                v = rootTag.getAttribute("version");
                level = v != null ? XsltChecker.getLanguageLevel(v.getValue()) : XsltChecker.LanguageLevel.NONE;
              }
              else {
                v = rootTag.getAttribute("version", XSLT_NS);
                level = v != null ? XsltChecker.getLanguageLevel(v.getValue()) : XsltChecker.LanguageLevel.NONE;
              }
              return CachedValueProvider.Result.create(level, rootTag);
            }
          }
        }
      }

      final XsltChecker xsltChecker = new XsltChecker();
      NanoXmlUtil.parseFile(psiFile, xsltChecker);
      return CachedValueProvider.Result.create(xsltChecker.getLanguageLevel(), psiFile);
    }
  }
}
