/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  @NotNull private PyImportedModule myImportedModule;

  public PyImportedModuleType(@NotNull PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    final PsiElement resolved = myImportedModule.resolve();
    if (resolved instanceof PyFile) {
      final PyFile file = (PyFile)resolved;
      return new PyModuleType(file, myImportedModule).resolveMember(name, location, direction, resolveContext);
    }
    else if (resolved instanceof PsiDirectory) {
      final List<PsiElement> elements = Collections.singletonList(ResolveImportUtil.resolveChild(resolved, name, null, true, true));
      return ResolveImportUtil.rateResults(elements);
    }
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(location);
    assert scopeOwner != null;
    final List<PyImportElement> importTargets = PyModuleType.getVisibleImports(scopeOwner);
    final int imported = myImportedModule.getImportedPrefix().getComponentCount();
    for (PyImportElement importTarget : importTargets) {
      final QualifiedName qName = importTarget.getImportedQName();
      if (qName != null && qName.matchesPrefix(myImportedModule.getImportedPrefix())) {
        final List<String> components = qName.getComponents();
        if (components.size() > imported) {
          String module = components.get(imported);
          result.add(LookupElementBuilder.create(module));
        }
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  public String getName() {
    return "imported module " + myImportedModule.getImportedPrefix().toString();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;  // no module can be imported from builtins
  }

  @Override
  public void assertValid(String message) {
  }

  @NotNull
  public PyImportedModule getImportedModule() {
    return myImportedModule;
  }
}
