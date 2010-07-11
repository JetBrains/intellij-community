/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.xml.dom.impl;

import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.resolve.DefinitionResolver;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.intellij.plugins.relaxNG.xml.dom.RngParentRef;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.08.2007
 */
public class DefinitionReference extends PsiReferenceBase.Poly<XmlAttributeValue>
        implements QuickFixProvider<DefinitionReference>, LocalQuickFixProvider,
        EmptyResolveMessageProvider, Function<Define, ResolveResult> {

  private final boolean myIsParentRef;
  private final GenericAttributeValue<String> myValue;

  public DefinitionReference(GenericAttributeValue<String> value) {
    super(value.getXmlAttributeValue());
    myValue = value;
    myIsParentRef = value.getParent() instanceof RngParentRef;
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final RngGrammar scope = getScope();
    if (scope == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    final Set<Define> set = DefinitionResolver.resolve(scope, myValue.getValue());
    if (set == null || set.size() == 0) return ResolveResult.EMPTY_ARRAY;

    return ContainerUtil.map2Array(set, ResolveResult.class, this);
  }

  @Nullable
  public RngGrammar getScope() {
    RngGrammar scope = myValue.getParentOfType(RngGrammar.class, true);
    if (scope == null) {
      return null;
    }
    if (myIsParentRef) {
      scope = scope.getParentOfType(RngGrammar.class, true);
    }
    return scope;
  }

  public ResolveResult fun(Define define) {
    final XmlElement xmlElement = (XmlElement)define.getPsiElement();
    assert xmlElement != null;
    return new PsiElementResolveResult(xmlElement);
  }

  @NotNull
  public Object[] getVariants() {
    final RngGrammar scope = getScope();
    if (scope == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    final Map<String, Set<Define>> map = DefinitionResolver.getAllVariants(scope);
    if (map == null || map.size() == 0) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    return ContainerUtil.mapNotNull(map.values(), new Function<Set<Define>, Object>() {
      public Object fun(Set<Define> defines) {
        final Define define = defines.iterator().next();
        if (defines.size() == 0) {
          return null;
        } else {
          final PsiElement element = define.getPsiElement();
          if (element != null) {
            final PsiPresentableMetaData data = (PsiPresentableMetaData)((PsiMetaOwner)element).getMetaData();
            if (data != null) {
              return LookupValueFactory.createLookupValue(data.getName(), data.getIcon());
            } else {
              return define.getName();
            }
          } else {
            return define.getName();
          }
        }
      }
    }).toArray();
  }

  public LocalQuickFix[] getQuickFixes() {
    final XmlTag tag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
    assert tag != null;
    final RngGrammar scope = myValue.getParentOfType(RngGrammar.class, true);
    if (scope != null) {
      return new LocalQuickFix[]{ new CreatePatternFix(this) };
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  public void registerQuickfix(HighlightInfo info, final DefinitionReference reference) {
    assert reference == this;
    final XmlTag tag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
    assert tag != null;
    final RngGrammar scope = myValue.getParentOfType(RngGrammar.class, true);
    if (scope != null) {
      QuickFixAction.registerQuickFixAction(info, new CreatePatternFix(this));
    }
  }

  public String getUnresolvedMessagePattern() {
    return "Unresolved pattern reference ''{0}''";
  }
}
