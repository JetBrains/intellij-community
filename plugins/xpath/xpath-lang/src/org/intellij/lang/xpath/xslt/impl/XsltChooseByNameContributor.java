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

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class XsltChooseByNameContributor implements ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    FileBasedIndex.getInstance().processAllKeys(XsltSymbolIndex.NAME, processor, scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    PsiManager psiManager = PsiManager.getInstance(parameters.getProject());
    FileBasedIndex.getInstance().processValues(XsltSymbolIndex.NAME, name, null, (file, kind) -> {
      if (kind == null) return true;
      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile == null || !XsltSupport.isXsltFile(psiFile)) return true;
      XmlTag root = ((XmlFile)psiFile).getRootTag();
      if (root == null) return true;
      JBIterable<XmlTag> tags =
        kind == XsltSymbolIndex.Kind.ANYTHING
        ? JBIterable.<XmlTag>empty()
          .append(root.findSubTags("variable", XsltSupport.XSLT_NS))
          .append(root.findSubTags("param", XsltSupport.XSLT_NS))
          .append(root.findSubTags("template", XsltSupport.XSLT_NS))
        : JBIterable.of(root.findSubTags(StringUtil.toLowerCase(kind.name()), XsltSupport.XSLT_NS));

      return tags.processEach(tag -> {
        XsltElement el = kind.wrap(tag);
        if (el instanceof PsiNamedElement && el instanceof NavigationItem) {
          if (name.equals(((PsiNamedElement)el).getName())) {
            return processor.process((NavigationItem)el);
          }
        }
        return true;
      });
    }, parameters.getSearchScope(), parameters.getIdFilter());
  }

}
