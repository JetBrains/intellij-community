/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;

import java.util.*;

public class ContainerUtil {
  public static List mergeSortedLists(List list1, List list2, Comparator comparator, boolean mergeEqualItems){
    ArrayList result = new ArrayList();

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

  public static <T> T find(Object[] array, Condition<T> condition) {
    for (int i = 0; i < array.length; i++) {
      T element = (T)array[i];
      if (condition.value(element)) return element;
    }
    return null;
  }

  public static <T> T find(Iterator<T> iterator, Condition<T> condition) {
    while (iterator.hasNext()) {
      T value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
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

  public static <T> ArrayList<T> collect(Iterator iterator, FilteringIterator.InstanceOf<T> instanceOf) {
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
}
