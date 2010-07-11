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

package org.intellij.plugins.relaxNG.model.annotation;

import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.Grammar;
import org.intellij.plugins.relaxNG.model.resolve.GrammarFactory;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

class OverriddenDefineRenderer extends GutterIconRenderer {
  private static final Icon ICON = IconLoader.getIcon("/gutter/overridenMethod.png");

  private final Define myDefine;

  public OverriddenDefineRenderer(Define define) {
    myDefine = define;
  }

  @NotNull
  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        final PsiElement element = myDefine.getPsiElement();
        if (element == null || !element.isValid()) return;

        final PsiElementProcessor.CollectElements<XmlFile> collector = new PsiElementProcessor.CollectElements<XmlFile>();
        final XmlFile localFile = (XmlFile)element.getContainingFile();
        RelaxIncludeIndex.processBackwardDependencies(localFile, collector);
        final Collection<XmlFile> files = collector.getCollection();

        final List<Define> result = new SmartList<Define>();
        final OverriddenDefineSearcher searcher = new OverriddenDefineSearcher(myDefine, localFile, result);
        for (XmlFile file : files) {
          final Grammar grammar = GrammarFactory.getGrammar(file);
          if (grammar == null) continue;
          grammar.acceptChildren(searcher);
        }

        if (result.size() > 0) {
          OverridingDefineRenderer.doClickAction(e, result, "Go to overriding define(s)");
        }
      }
    };
  }

  public boolean isNavigateAction() {
    return true;
  }

  @Nullable
  public String getTooltipText() {
    return "Is overridden";
  }
}