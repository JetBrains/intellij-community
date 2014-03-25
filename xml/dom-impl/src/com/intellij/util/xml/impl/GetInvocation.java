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
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class GetInvocation implements Invocation {
  private static final Key<CachedValue<List<Pair<Converter,Object>>>> DOM_VALUE_KEY = Key.create("Dom element value key");
  private final Converter myConverter;

  protected GetInvocation(final Converter converter) {
    assert converter != null;
    myConverter = converter;
  }

  @Override
  public Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable {
    if (myConverter == Converter.EMPTY_CONVERTER) {
      return getValueInner(handler, myConverter);
    }

    CachedValue<List<Pair<Converter,Object>>> value = handler.getUserData(DOM_VALUE_KEY);
    if (value == null) {
      final DomManagerImpl domManager = handler.getManager();
      final Project project = domManager.getProject();
      final CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(project);
      handler.putUserData(DOM_VALUE_KEY, value = cachedValuesManager.createCachedValue(new CachedValueProvider<List<Pair<Converter,Object>>>() {
        @Override
        public Result<List<Pair<Converter,Object>>> compute() {
          List<Pair<Converter, Object>> list = ContainerUtil.createLockFreeCopyOnWriteList();
          return Result
            .create(list, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, domManager, ProjectRootManager.getInstance(project));
        }
      }, false));
    }

    return getOrCalcValue(handler, value.getValue());
  }

  @Nullable
  private Object getOrCalcValue(final DomInvocationHandler<?, ?> handler, final List<Pair<Converter, Object>> list) {
    if (!list.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < list.size(); i++) {
        Pair<Converter, Object> pair = list.get(i);
        if (pair.first == myConverter) return pair.second;
      }
    }
    final Object returnValue = getValueInner(handler, myConverter);
    list.add(Pair.create(myConverter, returnValue));
    return returnValue;
  }

  @Nullable
  private static Object getValueInner(DomInvocationHandler<?, ?> handler, Converter converter) {
    final SubTag annotation = handler.getAnnotation(SubTag.class);
    if (annotation != null && annotation.indicator()) {
      final boolean tagNotNull = handler.getXmlTag() != null;
      if (converter == Converter.EMPTY_CONVERTER) {
        return tagNotNull ? "" : null;
      }
      else {
        return tagNotNull;
      }
    }

    String tagValue = handler.getValue();
    ConvertContext context = ConvertContextFactory.createConvertContext(handler);

    for (DomReferenceInjector each : DomUtil.getFileElement(handler).getFileDescription().getReferenceInjectors()) {
      tagValue = each.resolveString(tagValue, context);
    }

    return converter.fromString(tagValue, context);
  }

}
