/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author yole
*/
public class ResolveResultList extends ArrayList<RatedResolveResult> {
  public static List<RatedResolveResult> to(PsiElement element) {
    if (element== null) {
      return Collections.emptyList();
    }
    final ResolveResultList list = new ResolveResultList();
    list.poke(element, RatedResolveResult.RATE_NORMAL);
    return list;
  }

  public static List<? extends RatedResolveResult> asImportedResults(@Nullable List<? extends RatedResolveResult> from,
                                                                     @Nullable PyImportedNameDefiner nameDefiner) {
    if (ContainerUtil.isEmpty(from)) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(from, res -> new ImportedResolveResult(res.getElement(), res.getRate(), nameDefiner));
  }


  public static List<PsiElement> getElements(@NotNull List<? extends RatedResolveResult> from) {
    return Lists.transform(from, res -> res != null ? res.getElement() : null);
  }

  // Allows to add non-null elements and discard nulls in a hassle-free way.

  public boolean poke(final PsiElement what, final int rate) {
    PyPsiUtils.assertValid(what);
    if (what == null) return false;
    if (!(what instanceof LightElement) && !what.isValid()) {
      throw new PsiInvalidElementAccessException(what, "Trying to resolve a reference to an invalid element");
    }
    super.add(new RatedResolveResult(rate, what));
    return true;
  }
}
