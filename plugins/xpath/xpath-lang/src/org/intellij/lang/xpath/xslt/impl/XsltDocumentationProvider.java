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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ResourceUtil;
import com.intellij.xml.util.XmlUtil;
import org.intellij.lang.xpath.completion.ElementProvider;
import org.intellij.lang.xpath.completion.FunctionLookup;
import org.intellij.lang.xpath.psi.XPathFunction;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.impl.XsltLanguage;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.xpath.XPathFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class XsltDocumentationProvider implements DocumentationProvider {
  private final Supplier<Templates> templateSupplier = NotNullLazyValue.softLazy(() -> {
    try {
      TransformerFactory factory = TransformerFactory.newDefaultInstance();
      // use URL - resolves sub file
      URL resource = XsltSupport.class.getResource("resources/documentation.xsl");
      return factory.newTemplates(new StreamSource(Objects.requireNonNull(resource).openStream(), resource.toExternalForm()));
    }
    catch (TransformerConfigurationException | IOException e) {
      throw new RuntimeException(e);
    }
  });

  private final Supplier<String> documentSupplier = NotNullLazyValue.softLazy(() -> {
    try {
      byte[] data = ResourceUtil.getResourceAsBytes("org/intellij/lang/xpath/xslt/resources/documentation.xml",
                                                     XsltSupport.class.getClassLoader());
      return new String(Objects.requireNonNull(data), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  });

  @Override
  public @Nullable List<String> getUrlFor(PsiElement psiElement, PsiElement psiElement1) {
    if (psiElement instanceof XsltElement) {
      return null;
    }

    final String category;
    final String name;
    final String tagName = getTagName(psiElement);
    if (tagName != null) {
      name = tagName;
      category = "element";
    }
    else if (psiElement instanceof XPathFunction) {
      name = ((XPathFunction)psiElement).getName();
      category = "function";
    }
    else if (psiElement instanceof DocElement) {
      name = ((DocElement)psiElement).getName();
      category = ((DocElement)psiElement).getCategory();
    }
    else {
      return null;
    }

    try {
      Element element = JDOMUtil.load(documentSupplier.get());
      Element e = (Element)XPathFactory.instance()
        .compile("//x:" + category + "[@name = '" + name + "']", null, null, Namespace.getNamespace("x", element.getNamespaceURI()))
        .evaluateFirst(element);
      if (e != null) {
        return Collections.singletonList(e.getParentElement().getAttributeValue("base") + e.getAttributeValue("href"));
      }
    }
    catch (Exception e) {
      Logger.getInstance(getClass().getName()).error(e);
    }
    return null;
  }

  @Override
  public @Nullable @Nls String generateDoc(PsiElement psiElement, PsiElement psiElement1) {
    if (psiElement instanceof DocElement element) {
      return getDocumentation(element.getName(), element.getCategory());
    }

    if (psiElement instanceof XsltElement) {
      final XmlTag t = ((XsltElement)psiElement).getTag();
      PsiElement p = t.getPrevSibling();
      while (p instanceof PsiWhiteSpace || p instanceof XmlText) {
        p = p.getPrevSibling();
      }
      if (p instanceof XmlComment) {
        final String commentText = ((XmlComment)p).getCommentText();
        return commentText.replaceAll("&", "&amp;").replaceAll("<", "&lt;");
      }
      else {
        return null;
      }
    }

    final String name = getTagName(psiElement);
    if (name != null) {
      return getDocumentation(name, "element");
    }
    else if (psiElement instanceof XPathFunction) {
      return getDocumentation(((XPathFunction)psiElement).getName(), "function");
    }

    return null;
  }

  private static final Pattern check = Pattern.compile("x:found=\"(true|false)\"");

  private @NlsSafe @Nullable String getDocumentation(String name, String type) {
    try {
      Transformer transformer = templateSupplier.get().newTransformer();
      transformer.setParameter("element", name);
      transformer.setParameter("type", type);
      StringWriter writer = new StringWriter();
      // DOMSource doesn't work
      transformer.transform(new StreamSource(new StringReader(documentSupplier.get())), new StreamResult(writer));

      String s = writer.toString();
      Matcher matcher = check.matcher(s);
      if (matcher.find()) {
        if (matcher.group(1).equals("true")) {
          return s.replaceFirst("<META.+?>", "");
        }
      }
    }
    catch (Exception e) {
      Logger.getInstance(getClass().getName()).error(e);
    }
    return null;
  }

  private static @Nullable String getTagName(@Nullable PsiElement psiElement1) {
    XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement1, XmlTag.class, false);
    if (xmlTag != null) {
      if (XsltSupport.isXsltTag(xmlTag)) {
        return xmlTag.getLocalName();
      }
      else if (XmlUtil.ourSchemaUrisList.contains(xmlTag.getNamespace())) {
        PsiFile file = xmlTag.getContainingFile();
        if (file instanceof XmlFile) {
          XmlTag tag = ((XmlFile)file).getRootTag();
          if (tag != null && XsltSupport.XSLT_NS.equals(tag.getAttributeValue("targetNamespace"))) {
            return xmlTag.getAttributeValue("name");
          }
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager mgr, Object object, PsiElement psiElement) {
    if (object instanceof String) {
      if (psiElement instanceof XmlElement) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
        if (tag != null && XsltSupport.XSLT_NS.equals(tag.getNamespace())) {
          final String prefix = tag.getNamespacePrefix();
          if (prefix.length() == 0) {
            return new DocElement(mgr, psiElement, "element", (String)object);
          }
          else if (StringUtil.startsWithConcatenation(((String)object), prefix, ":")) {
            return new DocElement(mgr, psiElement, "element", ((String)object).substring(prefix.length() + 1));
          }
        }
      }
    }
    if (object instanceof FunctionLookup lookup) {
      return new DocElement(mgr, psiElement, "function", lookup.getName());
    }
    else if (object instanceof ElementProvider) {
      return ((ElementProvider)object).getElement();
    }
    if (object instanceof XsltElement) {
      return (PsiElement)object;
    }
    return null;
  }

  @Override
  public @Nullable PsiElement getDocumentationElementForLink(PsiManager mgr, String string, PsiElement psiElement) {
    final String[] strings = string.split("\\$");
    if (strings.length == 2) {
      return new DocElement(mgr, psiElement, strings[0], strings[1]);
    }
    return null;
  }

  static final class DocElement extends LightElement implements PsiNamedElement {
    private final PsiElement myElement;
    private final String myCategory;
    private final String myName;

    private DocElement(PsiManager mgr, PsiElement element, String category, String name) {
      super(mgr, XsltLanguage.INSTANCE);
      myElement = element;
      myCategory = category;
      myName = name;
    }

    public String getCategory() {
      return myCategory;
    }

    @Override
    public PsiElement setName(@NotNull @NonNls String name) throws IncorrectOperationException {
      throw new IncorrectOperationException("Unsupported");
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String toString() {
      return "DocElement";
    }

    @Override
    public PsiElement copy() {
      return this;
    }

    @Override
    public boolean isValid() {
      return myElement != null && myElement.isValid();
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
      if (!isValid()) {
        return null;
      }

      PsiFile file = myElement.getContainingFile();
      final PsiElement context = myElement.getContext();
      if (file == null && context != null) {
        file = context.getContainingFile();
      }
      PsiFile f;
      if ((f = PsiTreeUtil.getContextOfType(file, PsiFile.class, true)) instanceof XmlFile) {
        return f;
      }
      return file;
    }
  }
}