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

package org.intellij.plugins.relaxNG;

import com.intellij.navigation.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.intellij.plugins.relaxNG.model.CommonElement;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.Grammar;
import org.intellij.plugins.relaxNG.model.resolve.GrammarFactory;
import org.intellij.plugins.relaxNG.model.resolve.RelaxSymbolIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;

public class GotoSymbolContributor implements ChooseByNameContributorEx {

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    FileBasedIndex.getInstance().processAllKeys(RelaxSymbolIndex.NAME, processor, scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    boolean[] result = {true};
    PsiManager psiManager = PsiManager.getInstance(parameters.getProject());
    FileBasedIndex.getInstance().getFilesWithKey(
      RelaxSymbolIndex.NAME, Collections.singleton(name), file -> {
        PsiFile psiFile = psiManager.findFile(file);
        Grammar grammar = psiFile instanceof XmlFile ? GrammarFactory.getGrammar((XmlFile)psiFile) : null;
        if (grammar == null) return true;
        grammar.acceptChildren(new CommonElement.Visitor() {
          @Override
          public void visitDefine(Define define) {
            if (!result[0]) return;
            if (name.equals(define.getName())) {
              NavigationItem wrapped = wrap(define.getPsiElement());
              result[0] = wrapped == null || processor.process(wrapped);
            }
          }
        });
        return result[0];
      }, parameters.getSearchScope());
  }

  static @Nullable NavigationItem wrap(@Nullable PsiElement item) {
    if (!(item instanceof NavigationItem)) return null;
    PsiMetaData metaData0 = item instanceof PsiMetaOwner ? ((PsiMetaOwner)item).getMetaData() : null;
    PsiPresentableMetaData metaData = metaData0 instanceof PsiPresentableMetaData ? (PsiPresentableMetaData)metaData0 : null;
    ItemPresentation presentation = metaData != null ? new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return metaData.getName();
      }

      @Override
      public @NotNull String getLocationString() {
        return MyNavigationItem.getLocationString(item);
      }

      @Override
      public @Nullable Icon getIcon(boolean open) {
        return metaData.getIcon();
      }

      @Override
      public @Nullable TextAttributesKey getTextAttributesKey() {
        ItemPresentation p = ((NavigationItem)item).getPresentation();
        return p instanceof ColoredItemPresentation ? ((ColoredItemPresentation)p).getTextAttributesKey() : null;
      }
    } : ((NavigationItem)item).getPresentation();
    return presentation == null ? null : new MyNavigationItem((NavigationItem)item, presentation);
  }

  private static final class MyNavigationItem implements PsiElementNavigationItem, ItemPresentation {
    final NavigationItem myItem;
    final ItemPresentation myPresentation;
    final String myLocationString;

    private MyNavigationItem(NavigationItem item, final @NotNull ItemPresentation presentation) {
      myItem = item;
      myPresentation = presentation;
      myLocationString = getLocationString((PsiElement)myItem);
    }

    @Override
    public String getPresentableText() {
      return myPresentation.getPresentableText();
    }

    @Override
    public @Nullable String getLocationString() {
      return myLocationString;
    }

    private static String getLocationString(PsiElement element) {
      return "(in " + element.getContainingFile().getName() + ")";
    }

    @Override
    public @Nullable Icon getIcon(boolean open) {
      return myPresentation.getIcon(open);
    }

    public @Nullable TextAttributesKey getTextAttributesKey() {
      return myPresentation instanceof ColoredItemPresentation ? ((ColoredItemPresentation)myPresentation).getTextAttributesKey() : null;
    }

    @Override
    public String getName() {
      return myItem.getName();
    }

    @Override
    public ItemPresentation getPresentation() {
      return this;
    }

    @Override
    public PsiElement getTargetElement() {
      return (PsiElement)myItem;
    }

    @Override
    public void navigate(boolean requestFocus) {
      myItem.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return myItem.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return myItem.canNavigateToSource();
    }
  }
}