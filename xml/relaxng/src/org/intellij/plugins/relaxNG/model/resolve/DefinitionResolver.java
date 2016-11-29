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

package org.intellij.plugins.relaxNG.model.resolve;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import gnu.trove.THashSet;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.model.*;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.08.2007
*/
public class DefinitionResolver extends CommonElement.Visitor implements
        CachedValueProvider<Map<String, Set<Define>>>, Factory<Set<Define>> {

  private static final Key<CachedValue<Map<String, Set<Define>>>> KEY = Key.create("CACHED_DEFINES");

  private static final ThreadLocal<Set<PsiFile>> myVisitedFiles = new ThreadLocal<>();
  private static final ThreadLocal<Map<String, Set<Define>>> myDefines = new ThreadLocal<Map<String, Set<Define>>>() {
    @Override
    protected Map<String, Set<Define>> initialValue() {
      return ContainerUtil.newHashMap();
    }
  };

  private final Grammar myScope;

  private DefinitionResolver(Grammar scope) {
    myScope = scope;
  }

  @Override
  public void visitInclude(Include include) {
    include.acceptChildren(this);

    final PsiFile value = include.getInclude();
    if (myVisitedFiles.get() == null) {
      myVisitedFiles.set(ContainerUtil.<PsiFile>newIdentityTroveSet());
    }
    if (value != null && myVisitedFiles.get().add(value)) {
      doVisitRncOrRngFile(value, this);
    }
  }

  private static void doVisitRncOrRngFile(PsiFile file, CommonElement.Visitor visitor) {
    if (file instanceof RncFile) {
      final Grammar grammar = ((RncFile)file).getGrammar();
      if (grammar != null) {
        grammar.acceptChildren(visitor);
      }
    } else if (file instanceof XmlFile) {
      final DomManager mgr = DomManager.getDomManager(file.getProject());
      final DomFileElement<RngGrammar> element = mgr.getFileElement((XmlFile)file, RngGrammar.class);
      if (element != null) {
        element.getRootElement().acceptChildren(visitor);
      }
    }
  }

  @Override
  public void visitDiv(Div div) {
    div.acceptChildren(this);
  }

  @Override
  public void visitDefine(Define def) {
    ContainerUtil.getOrCreate(myDefines.get(), def.getName(), this).add(def);
  }

  @Override
  public void visitPattern(Pattern pattern) {
  }

  @Override
  public void visitGrammar(Grammar pattern) {
  }

  @Override
  public void visitRef(Ref ref) {
  }


  @Override
  public Set<Define> create() {
    return new THashSet<>();
  }

  @Override
  public Result<Map<String, Set<Define>>> compute() {
    try {
      myScope.acceptChildren(this);

      final PsiElement psiElement = myScope.getPsiElement();
      if (psiElement == null || !psiElement.isValid()) {
        return Result.create(null, ModificationTracker.EVER_CHANGED);
      }

      final PsiFile file = psiElement.getContainingFile();
      if (myVisitedFiles.get() != null) {
        myVisitedFiles.get().add(file);
        return Result.create(myDefines.get(), myVisitedFiles.get().toArray());
      } else {
        return Result.create(myDefines.get(), file);
      }
    } finally {
      myVisitedFiles.remove();
      myDefines.remove();
    }
  }

  @Nullable
  public static Set<Define> resolve(Grammar scope, final String value) {
    final Map<String, Set<Define>> map = getAllVariants(scope);
    if (map == null) {
      return null;
    }

    final Set<Define> set = map.get(value);

    // actually we should always do this, but I'm a bit afraid of the performance impact
    if (set == null || set.size() == 0) {
      final PsiElement element = scope.getPsiElement();
      if (element != null) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof XmlFile) {
          final BackwardDefinitionResolver resolver = new BackwardDefinitionResolver(value);
          RelaxIncludeIndex.processBackwardDependencies((XmlFile)file, resolver);
          return resolver.getResult();
        }
      }
    }

    return set;
  }

  @Nullable
  public static Map<String, Set<Define>> getAllVariants(Grammar scope) {
    final PsiElement psiElement = scope.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return null;

    final CachedValuesManager manager = CachedValuesManager.getManager(psiElement.getProject());
    CachedValue<Map<String, Set<Define>>> data = psiElement.getUserData(KEY);
    if (data == null || !((DefinitionResolver)data.getValueProvider()).isValid()) {
      final DefinitionResolver resolver = new DefinitionResolver(scope);
      data = manager.createCachedValue(resolver, false);
      psiElement.putUserData(KEY, data);
    }
    return data.getValue();
  }

  private boolean isValid() {
    final PsiElement element = myScope.getPsiElement();
    return element != null && element.isValid();
  }

  private static class BackwardDefinitionResolver implements PsiElementProcessor<XmlFile> {
    private final String myValue;
    private Define myResult;
    private final Set<PsiFile> myVisitedPsiFiles = new HashSet<>();

    public BackwardDefinitionResolver(String value) {
      myValue = value;
    }

    @Override
    public boolean execute(@NotNull XmlFile element) {
      final Grammar g = GrammarFactory.getGrammar(element);
      if (g != null) {
        g.acceptChildren(new CommonElement.Visitor() {
          @Override
          public void visitElement(CommonElement pattern) {
            if (myResult == null) {
              super.visitElement(pattern);
            }
          }

          @Override
          public void visitDefine(Define define) {
            if (myValue.equals(define.getName())) {
              myResult = define;
            }
          }

          @Override
          public void visitInclude(Include include) {
            final PsiFile file = include.getInclude();
            if (file != null && myVisitedPsiFiles.add(file)) {
              doVisitRncOrRngFile(file, this);
            }
          }
        });
      }
      return myResult == null;
    }

    @Nullable
    public Set<Define> getResult() {
      return myResult != null ? Collections.singleton(myResult) : null;
    }
  }
}