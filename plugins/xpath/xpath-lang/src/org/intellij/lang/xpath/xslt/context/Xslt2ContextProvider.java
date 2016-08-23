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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.FunctionContext;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltFunction;
import org.intellij.lang.xpath.xslt.psi.XsltStylesheet;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Xslt2ContextProvider extends XsltContextProviderBase {
  public static final ContextType TYPE = ContextType.lookupOrCreate("XSLT2", XPathVersion.V2);

  private static final Key<ParameterizedCachedValue<Map<Pair<QName,Integer>,Function>,XmlFile>> FUNCTIONS = Key.create("XSLT_FUNCTIONS");

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
          return ContainerUtil.union(base.getFunctions(), getCustomFunctions(xmlFile));
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

  private static final UserDataCache<ParameterizedCachedValue<Map<Pair<QName,Integer>,Function>,XmlFile>, XmlFile, Void> ourFunctionCacheProvider =
    new UserDataCache<ParameterizedCachedValue<Map<Pair<QName,Integer>,Function>,XmlFile>, XmlFile, Void>() {
    @Override
    protected ParameterizedCachedValue<Map<Pair<QName,Integer>,Function>,XmlFile> compute(XmlFile file, Void p) {
      return CachedValuesManager.getManager(file.getProject()).createParameterizedCachedValue(MyFunctionProvider.INSTANCE, false);
    }
  };

  private static Map<Pair<QName, Integer>, Function> getCustomFunctions(XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    // Simplified xslt syntax does not allow to declare custom functions (Saxon 9: "An html element must not contain an xsl:function element")
    if (rootTag != null && XsltSupport.isXsltRootTag(rootTag)) {
      return ourFunctionCacheProvider.get(FUNCTIONS, file, null).getValue(file);
    }
    return Collections.emptyMap();
  }

  @Nullable
  private static Function resolveCustomFunction(final XmlFile file, final QName name, int argCount) {
    final Map<Pair<QName, Integer>, Function> functions = getCustomFunctions(file);
    final Function exactMatch = functions.get(Pair.create(name, argCount));
    if (exactMatch != null) {
      return exactMatch;
    }

    Function candidate = null;
    for (Pair<QName, Integer> pair : functions.keySet()) {
      if (pair.getFirst().equals(name)) {
        candidate = functions.get(pair);
      }
    }
    return candidate;
  }

  private static class MyFunctionProvider implements ParameterizedCachedValueProvider<Map<Pair<QName, Integer>, Function>, XmlFile> {
    private static ParameterizedCachedValueProvider<Map<Pair<QName,Integer>,Function>,XmlFile> INSTANCE = new MyFunctionProvider();

    @Override
    public CachedValueProvider.Result<Map<Pair<QName, Integer>, Function>> compute(XmlFile param) {
      final XmlTag rootTag = param.getRootTag();
      assert rootTag != null;

      final Map<Pair<QName, Integer>, Function> candidates = new HashMap<>();
      final XsltFunction[] functions = XsltElementFactory.getInstance().wrapElement(rootTag, XsltStylesheet.class).getFunctions();
      for (XsltFunction function : functions) {
        candidates.put(Pair.create(function.getQName(), function.getParameters().length), function);
      }

      final Collection<XmlFile> data = ResolveUtil.getDependencies(param);
      final Object[] dependencies;
      if (data == null || data.size() == 0) {
        dependencies = new Object[]{ param };
      } else {
        data.add(param);
        dependencies = ArrayUtil.toObjectArray(data);
      }
      return CachedValueProvider.Result.create(candidates, dependencies);
    }
  }
}
