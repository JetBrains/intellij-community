/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.Collection;
import java.util.Map;

/**
 * User: lex
 * Date: Sep 25, 2003
 * Time: 12:16:58 AM
 */
public interface InternalIterator<T>{
  /**
   * @param element
   * @return false to stop iteration true to continue if more elements are avaliable.
   */
  boolean visit(T element);

  class Collector<T> implements InternalIterator<T> {
    private final Collection<T> myCollection;

    public Collector(Collection<T> collection) {
      myCollection = collection;
    }

    public boolean visit(T value) {
      return myCollection.add(value);
    }

    public static <T> InternalIterator<T> create(Collection<T> collection) {
      return new Collector<T>(collection);
    }
  }

  class MapFromValues<K, Dom, V extends Dom> implements InternalIterator<V> {
    private final Map<K, V> myMap;
    private final Convertor<Dom, K> myToKeyConvertor;

    public MapFromValues(Map<K, V> map, Convertor<Dom, K> toKeyConvertor) {
      myMap = map;
      myToKeyConvertor = toKeyConvertor;
    }

    public boolean visit(V value) {
      myMap.put(myToKeyConvertor.convert(value), value);
      return true;
    }

    public static <Dom, K, V extends Dom> InternalIterator<V> create(Convertor<Dom, K> toKey, Map<K, V> map) {
      return new MapFromValues<K, Dom, V>(map, toKey);
    }
  }

  class Filtering<T> implements InternalIterator<T> {
    private final com.intellij.openapi.util.Condition<T> myFilter;
    private final InternalIterator<T> myIterator;

    public Filtering(InternalIterator<T> iterator, com.intellij.openapi.util.Condition<T> filter) {
      myIterator = iterator;
      myFilter = filter;
    }

    public boolean visit(T value) {
      if (!myFilter.value(value)) return true;
      return myIterator.visit(value);
    }

    public static <T> InternalIterator<T> create(InternalIterator<T> iterator, com.intellij.openapi.util.Condition<T> filter) {
      return new Filtering<T>(iterator, filter);
    }

    public static <T, V extends T> InternalIterator<T> createInstanceOf(InternalIterator<V> iterator, FilteringIterator.InstanceOf<V> filter) {
      return new Filtering<T>((InternalIterator<T>)(InternalIterator)iterator,
                              (com.intellij.openapi.util.Condition<T>)(com.intellij.openapi.util.Condition)filter);
    }

    public static <T> InternalIterator createInstanceOf(InternalIterator<T> iterator, Class<T> aClass) {
      return createInstanceOf(iterator, FilteringIterator.instanceOf(aClass));
    }
  }

  class Converting<Dom, Rng> implements InternalIterator<Dom> {
    private final Convertor<Dom, Rng> myConvertor;
    private final InternalIterator<Rng> myIterator;

    public Converting(InternalIterator<Rng> iterator, Convertor<Dom, Rng> convertor) {
      myIterator = iterator;
      myConvertor = convertor;
    }

    public boolean visit(Dom element) {
      return myIterator.visit(myConvertor.convert(element));
    }

    public static <Dom, Rng> InternalIterator<Dom> create(Convertor<Dom, Rng> convertor, InternalIterator<Rng> iterator) {
      return new Converting<Dom, Rng>(iterator, convertor);
    }
  }
}
