// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public final class ModelMergerUtil {

  public static @Nullable <T> T getFirstImplementation(final T t) {
    T cur = t;
    while (cur instanceof MergedObject) {
      final List<T> implementations = ((MergedObject<T>)cur).getImplementations();
      cur = implementations.isEmpty()? null : implementations.get(0);
    }
    return cur;
  }

  public static @Nullable <T, V> V getImplementation(final Class<V> clazz, final Collection<T> elements) {
    for (final T element : elements) {
      final V implementation = getImplementation(element, clazz);
      if (implementation != null) {
        return implementation;
      }
    }
    return null;
  }

  public static @Nullable <T, V> V getImplementation(final Class<V> clazz, final T... elements) {
    return getImplementation(clazz, Arrays.asList(elements));
  }

  public static @Nullable <T, V> V getImplementation(final T element, final Class<V> clazz) {
    if (element == null) return null;
    CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<>() {
      @Override
      public boolean process(final T t) {
        return !ReflectionUtil.isAssignable(clazz, t.getClass()) || super.process(t);
      }
    };
    new ImplementationProcessor<>(processor, true).process(element);
    return (V)processor.getFoundValue();
  }

  public static @NotNull <T, V> Collection<V> getImplementations(final T element, final Class<V> clazz) {
    if (element == null) return Collections.emptyList();
    CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<>() {
      @Override
      public boolean process(final T t) {
        return !ReflectionUtil.isAssignable(clazz, t.getClass()) || super.process(t);
      }
    };
    new ImplementationProcessor<>(processor, true).process(element);
    return (Collection<V>)processor.getResults();
  }

  @Unmodifiable
  public static @NotNull <T> List<T> getImplementations(T element) {
    if (element instanceof MergedObject) {
      final MergedObject<T> mergedObject = (MergedObject<T>)element;
      return mergedObject.getImplementations();
    }
    else if (element != null) {
      return Collections.singletonList(element);
    }
    else {
      return Collections.emptyList();
    }
  }

  public static @NotNull <T> List<T> getFilteredImplementations(final T element) {
    if (element == null) return Collections.emptyList();
    List<T> result = new ArrayList<>();
    Processor<T> processor = Processors.cancelableCollectProcessor(result);
    new ImplementationProcessor<>(processor, false).process(element);
    return result;
  }

  public static @NotNull <T> Processor<T> createFilteringProcessor(final Processor<? super T> processor) {
    return new ImplementationProcessor<>(processor, false);
  }

  public static class ImplementationProcessor<T> implements Processor<T> {
    private final Processor<? super T> myProcessor;
    private final boolean myProcessMerged;

    public ImplementationProcessor(Processor<? super T> processor, final boolean processMerged) {
      myProcessor = processor;
      myProcessMerged = processMerged;
    }

    @Override
    public boolean process(final T t) {
      final boolean merged = t instanceof MergedObject;
      if ((!merged || myProcessMerged) && !myProcessor.process(t)) {
        return false;
      }
      if (merged && !ContainerUtil.process(((MergedObject<T>)t).getImplementations(), this)) {
        return false;
      }
      return true;
    }
  }
}
