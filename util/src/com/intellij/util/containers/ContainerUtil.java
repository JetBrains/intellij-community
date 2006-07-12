/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;

public class ContainerUtil {
  public static List<Object> mergeSortedLists(List<Object> list1, List<Object> list2, Comparator<Object> comparator, boolean mergeEqualItems){
    ArrayList<Object> result = new ArrayList<Object>();

    int index1 = 0;
    int index2 = 0;
    while(index1 < list1.size() || index2 < list2.size()){
      if (index1 >= list1.size()){
        result.add(list2.get(index2++));
      }
      else if (index2 >= list2.size()){
        result.add(list1.get(index1++));
      }
      else{
        Object element1 = list1.get(index1);
        Object element2 = list2.get(index2);
        int c = comparator.compare(element1,  element2);
        if (c < 0){
          result.add(element1);
          index1++;
        }
        else if (c > 0){
          result.add(element2);
          index2++;
        }
        else{
          result.add(element1);
          if (!mergeEqualItems){
            result.add(element2);
          }
          index1++;
          index2++;
        }
      }
    }

    return result;
  }

  public static <T> void addAll(Collection<T> collection, Iterator<T> iterator) {
    while (iterator.hasNext()) {
      T o = iterator.next();
      collection.add(o);
    }
  }

  public static <T> ArrayList<T> collect(Iterator<T> iterator) {
    ArrayList<T> list = new ArrayList<T>();
    addAll(list, iterator);
    return list;
  }

  public static <T> HashSet<T> collectSet(Iterator<T> iterator) {
    HashSet<T> hashSet = new HashSet<T>();
    addAll(hashSet, iterator);
    return hashSet;
  }

  public static <K,V> HashMap<K, V> assignKeys(Iterator<V> iterator, Convertor<V, K> keyConvertor) {
    HashMap<K, V> hashMap = new HashMap<K, V>();
    while (iterator.hasNext()) {
      V value = iterator.next();
      hashMap.put(keyConvertor.convert(value), value);
    }
    return hashMap;
  }

  public static <K, V> HashMap<K, V> assignValues(Iterator<K> iterator, Convertor<K, V> valueConvertor) {
    HashMap<K, V> hashMap = new HashMap<K, V>();
    while (iterator.hasNext()) {
      K key = iterator.next();
      hashMap.put(key, valueConvertor.convert(key));
    }
    return hashMap;
  }

  public static <T> Iterator<T> emptyIterator() {
    return new Iterator<T>() {
      public boolean hasNext() { return false; }
      public T next() { throw new NoSuchElementException(); }
      public void remove() { throw new IllegalStateException(); }
    };
  }

  public static <T> int findByEquals(T[] array, T element) {
    return findByEquals(Arrays.asList(array), element);
  }

  public static <T> int findByEquals(List<? extends T> list, T element) {
    for (int i = 0; i < list.size(); i++) {
      T t = list.get(i);
      if (element == null) {
        if (t == null) {
          return i;
        }
      } else {
        if (element.equals(t)) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  public static <T> T find(Object[] array, Condition<T> condition) {
    for (Object anArray : array) {
      T element = (T)anArray;
      if (condition.value(element)) return element;
    }
    return null;
  }

  public static <T> boolean process(Iterable<? extends T> iterable, Processor<T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static <T> T find(Iterable<? extends T> iterable, Condition<T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Nullable
  public static <T> T find(Iterable<? extends T> iterable, final T equalTo) {
    return find(iterable, new Condition<T>() {
      public boolean value(final T object) {
        return equalTo == object || equalTo.equals(object);
      }
    });
  }

  @Nullable
  public static <T> T find(Iterator<? extends T> iterator, Condition<T> condition) {
    while (iterator.hasNext()) {
      T value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
  }

  public static <T,V> List<V> map2List(T[] array, Function<T,V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  public static <T,V> List<V> map2List(Collection<? extends T> collection, Function<T,V> mapper) {
    final ArrayList<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  public static <T,V> Set<V> map2Set(T[] collection, Function<T,V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  public static <T,V> Set<V> map2Set(Collection<? extends T> collection, Function<T,V> mapper) {
    final HashSet<V> set = new HashSet<V>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  public static <T> Object[] map2Array(T[] array, Function<T,Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  public static <T> Object[] map2Array(Collection<T> array, Function<T,Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  public static <T,V> V[] map2Array(T[] array, Class<? extends V> aClass, Function<T,V> mapper) {
    return map2Array(Arrays.asList(array), aClass, mapper);
  }

  public static <T,V> V[] map2Array(Collection<? extends T> collection, Class<? extends V> aClass, Function<T,V> mapper) {
    final List<V> list = map2List(collection, mapper);
    return list.toArray((V[])Array.newInstance(aClass, list.size()));
  }

  public static <T,V> V[] map2Array(Collection<? extends T> collection, V[] to, Function<T,V> mapper) {
    return map2List(collection, mapper).toArray(to);
  }

  public static <T> List<T> findAll(T[] collection, Condition<? super T> condition) {
    return findAll(Arrays.asList(collection), condition);
  }

  public static <T> List<T> findAll(Collection<? extends T> collection, Condition<? super T> condition) {
    final ArrayList<T> result = new ArrayList<T>();
    for (final T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  public static <T> List<T> skipNulls (Collection<? extends T> collection) {
    return findAll(collection, Condition.NOT_NULL);
  }

  public static <T,V> List<V> findAll(Collection<? extends T> collection, Class<V> instanceOf) {
    final ArrayList<V> result = new ArrayList<V>();
    for (final T t : collection) {
      if (instanceOf.isInstance(t)) {
        result.add((V)t);
      }
    }
    return result;
  }

  public static <T> void removeDuplicates(Collection<T> collection) {
    Set<T> collected = new HashSet<T>();
    for (Iterator<T> iterator = collection.iterator(); iterator.hasNext();) {
      T t = iterator.next();
      if (!collected.contains(t)) {
        collected.add(t);
      } else {
        iterator.remove();
      }
    }
  }

  public static <T> Iterator<T> iterate(T[] arrays) {
    return Arrays.asList(arrays).iterator();
  }

  public static <T> Iterator<T> iterate(final Enumeration<T> enumeration) {
    return new Iterator<T>() {
      public boolean hasNext() {
        return enumeration.hasMoreElements();
      }

      public T next() {
        return enumeration.nextElement();
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static <E> void swapElements(final List<E> list, final int index1, final int index2) {
    E e1 = list.get(index1);
    E e2 = list.get(index2);
    list.set(index1, e2);
    list.set(index2, e1);
  }

  public static <T> ArrayList<T> collect(Iterator<?> iterator, FilteringIterator.InstanceOf<T> instanceOf) {
    return collect(FilteringIterator.create(iterator, instanceOf));
  }

  public static <T> void addAll(Collection<T> collection, Enumeration<T> enumeration) {
    while (enumeration.hasMoreElements()) {
      T element = enumeration.nextElement();
      collection.add(element);
    }
  }

  public static <T, U extends T> T findInstance(Iterator<T> iterator, Class<U> aClass) {
    // uncomment for 1.5
    //return (U)find(iterator, new FilteringIterator.InstanceOf<U>(aClass));
    return (T)find(iterator, new FilteringIterator.InstanceOf<T>((Class<T>)aClass));
  }

  public static <T,V> List<T> concat(V[] array, Function<V,Collection<T>> fun) {
    return concat(Arrays.asList(array), fun);
  }

  public static <T,V> List<T> concat(Iterable<V> list, Function<V,Collection<T>> fun) {
    final ArrayList<T> result = new ArrayList<T>();
    for (final V v : list) {
      result.addAll(fun.fun(v));
    }
    return result;
  }

  public static <T> boolean intersects(Collection<T> collection1, Collection<T> collection2) {
    for (T t : collection1) {
      if (collection2.contains(t)) {
        return true;
      }
    }
    return false;
  }

  public static <T> Collection<T> subtract(Collection<T> from, Collection<T> what) {
    final HashSet<T> set = new HashSet<T>(from);
    set.removeAll(what);
    return set;
  }

  public static <T> T[] toArray(List<T> collection, T[] array){
    final int length = array.length;
    if (length < 20) {
      for (int i = 0; i < collection.size(); i++) {
        array[i] = collection.get(i);
      }
      return array;
    }
    else {
      return collection.toArray(array);
    }
  }

  public static <T,V> List<V> map(Iterable<? extends T> iterable, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T,V> List<V> mapNotNull(T[] array, Function<T, V> mapping) {
    return mapNotNull(Arrays.asList(array), mapping);
  }
  
  public static <T,V> List<V> mapNotNull(Iterable<? extends T> iterable, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result;
  }

  public static <T,V> List<V> map(T[] arr, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : arr) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T> void addIfNotNull(final T element, final Collection<T> result) {
    if (element != null) {
      result.add(element);
    }
  }

  public static <T> void add(final T element, final Collection<T> result, final Disposable parentDisposable) {
    result.add(element);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        result.remove(element);
      }
    });
  }
}
