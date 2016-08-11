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

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.intellij.plugins.relaxNG.model.CommonElement;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.Grammar;
import org.intellij.plugins.relaxNG.model.Include;
import org.intellij.plugins.relaxNG.model.resolve.DefinitionResolver;
import org.intellij.plugins.relaxNG.model.resolve.GrammarFactory;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;
import org.intellij.plugins.relaxNG.xml.dom.RngDomElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 04.12.2007
*/
public final class ModelAnnotator implements Annotator, DomElementsAnnotator {

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof CommonElement) {
      ((CommonElement)psiElement).accept(new MyAnnotator<>(CommonAnnotationHolder.create(holder)));
    }
  }

  @Override
  public void annotate(DomElement element, DomElementAnnotationHolder holder) {
    if (element instanceof RngDomElement) {
      ((RngDomElement)element).accept(new MyAnnotator<>(CommonAnnotationHolder.create(holder)));
    }
  }

  private final class MyAnnotator<T> extends CommonElement.Visitor {
    private final CommonAnnotationHolder<T> myHolder;

    public MyAnnotator(CommonAnnotationHolder<T> holder) {
      myHolder = holder;
    }

    @Override
    public void visitDefine(final Define define) {
      final PsiElement element = define.getPsiElement();
      if (element != null) {
        final XmlFile xmlFile = (XmlFile)element.getContainingFile();

        final List<Define> result = new SmartList<>();
        final OverriddenDefineSearcher searcher = new OverriddenDefineSearcher(define, xmlFile, result);

        final PsiElementProcessor.FindElement<XmlFile> processor = new PsiElementProcessor.FindElement<XmlFile>() {
          @Override
          public boolean execute(@NotNull XmlFile file) {
            final Grammar grammar = GrammarFactory.getGrammar(file);
            if (grammar == null) return true;

            grammar.acceptChildren(searcher);

            return result.size() == 0 || super.execute(file);
          }
        };
        
        RelaxIncludeIndex.processBackwardDependencies(xmlFile, processor);

        if (processor.isFound()) {
          createGutterAnnotation(define, new OverriddenDefineRenderer(define));
        }
      }
    }

    @SuppressWarnings({ "unchecked" })
    private void createGutterAnnotation(CommonElement t, GutterIconRenderer renderer) {
      final Annotation a = myHolder.createAnnotation((T)t, HighlightSeverity.INFORMATION, null);
      a.setGutterIconRenderer(renderer);
    }

    @Override
    public void visitInclude(Include inc) {
      final Define[] overrides = inc.getOverrides();
      for (Define define : overrides) {
        final PsiFile file = inc.getInclude();
        if (!(file instanceof XmlFile)) continue; //

        final Grammar grammar = GrammarFactory.getGrammar((XmlFile)file);
        if (grammar == null) continue;

        final Map<String, Set<Define>> map = DefinitionResolver.getAllVariants(grammar);
        if (map == null) continue;

        final Set<Define> set = map.get(define.getName());
        if (set == null || set.size() == 0) {
          //noinspection unchecked
          myHolder.createAnnotation((T)define, HighlightSeverity.ERROR, "Definition doesn't override anything from " + file.getName());
          continue;
        }

        final String message = "Overrides '" + define.getName() + "' in " + file.getName();
        createGutterAnnotation(define, new OverridingDefineRenderer(message, set));
      }
    }
  }
}