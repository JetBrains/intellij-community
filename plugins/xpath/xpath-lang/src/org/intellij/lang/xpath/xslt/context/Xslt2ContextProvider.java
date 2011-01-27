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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.collections.map.CompositeMap;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.FunctionContext;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltFunction;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

public class Xslt2ContextProvider extends XsltContextProviderBase {
  public static final ContextType TYPE = ContextType.lookupOrCreate("XSLT2", XPathVersion.V2);

  protected Xslt2ContextProvider(@NotNull XmlElement contextElement) {
    super(contextElement);
  }

  @NotNull
  @Override
  public ContextType getContextType() {
    return TYPE;
  }

  @Override
  protected XPathType getTypeForTag(XmlTag tag, String attribute) {
    if ("select".equals(attribute)) {
      final String tagName = tag.getLocalName();

      if ("sequence".equals(tagName)) {
        final XPathType declaredType = XsltCodeInsightUtil.getDeclaredType(tag);
        if (declaredType != null) {
          return declaredType;
        }

        if (XsltSupport.isFunction(tag.getParentTag())) {
          final XsltFunction func = XsltElementFactory.getInstance().wrapElement(tag.getParentTag(), XsltFunction.class);
          return func.getReturnType();
        }
        return XPath2Type.SEQUENCE;
      } else if ("value-of".equals(tagName) || "copy-of".equals(tagName) || "for-each".equals(tagName)) {
        return XPath2Type.SEQUENCE;
      }
    } else if ("group-by".equals(attribute)) {
      return XPath2Type.ITEM;
    }
    return super.getTypeForTag(tag, attribute);
  }

  private static final UserDataCache<FunctionContext, XmlFile, Void> functionContextCache =
          new UserDataCache<FunctionContext, XmlFile, Void>("xslt2FunctionContext") {

    @Override
    protected FunctionContext compute(final XmlFile xmlFile, Void p) {
      final FunctionContext base = Xslt2FunctionContext.getInstance();
      return new FunctionContext() {
        @Override
        public Map<Pair<QName, Integer>, Function> getFunctions() {
          return new CompositeMap(base.getFunctions(), getCustomFunctions(xmlFile));
        }

        @Override
        public boolean allowsExtensions() {
          return base.allowsExtensions();
        }

        @Override
        public Function resolve(QName name, int argCount) {
          final Function f = base.resolve(name, argCount);
          if (f == null) {
            return resolveCustomFunction(xmlFile, name, argCount);
          }
          return f;
        }
      };
    }
  };

  @Override
  @NotNull
  public FunctionContext createFunctionContext() {
    final XmlElement contextElement = getContextElement();
    return contextElement != null && contextElement.isValid() ?
            functionContextCache.get((XmlFile)contextElement.getContainingFile(), null) :
            Xslt2FunctionContext.getInstance();
  }

  private static Map<Pair<QName, Integer>, Function> getCustomFunctions(XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    assert rootTag != null;

    final XmlTag[] functionTags = rootTag.findSubTags("function", XsltSupport.XSLT_NS);

    final Map<Pair<QName, Integer>, Function> candidates = new HashMap<Pair<QName, Integer>, Function>();
    for (XmlTag tag : functionTags) {
      final String n = tag.getAttributeValue("name");
      if (n == null) continue;

      final XsltFunction f = XsltElementFactory.getInstance().wrapElement(tag, XsltFunction.class);
      candidates.put(Pair.create(f.getQName(), f.getParameters().length), f);
    }
    return candidates;
  }

  @Nullable
  private static Function resolveCustomFunction(final XmlFile file, final QName name, int argCount) {
    final XmlTag rootTag = file.getRootTag();
    assert rootTag != null;

    final XmlTag[] functionTags = rootTag.findSubTags("function", XsltSupport.XSLT_NS);

    XsltFunction candidate = null;
    for (XmlTag tag : functionTags) {
      final String n = tag.getAttributeValue("name");
      if (n == null) continue;

      final QName fName = QNameUtil.createQName(n, tag);
      if (fName.equals(name)) {
        candidate = XsltElementFactory.getInstance().wrapElement(tag, XsltFunction.class);

        if (candidate.getParameters().length == argCount) { // TODO: overrride=[yes|no]
          return candidate;
        }
      }
    }

    return candidate;
  }
}
