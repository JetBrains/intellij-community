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
package com.intellij.util;

import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.Equality;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * Author: msk
 */
public class ArrayUtil {
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final char[] EMPTY_CHAR_ARRAY = new char[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final int [] EMPTY_INT_ARRAY = new int[0];
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final CharSequence EMPTY_CHAR_SEQUENCE = new CharArrayCharSequence(EMPTY_CHAR_ARRAY);

  public static byte[] realloc (final byte [] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_BYTE_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final byte [] result = new byte [newSize];
    System.arraycopy(array, 0, result, 0, Math.min (oldSize, newSize));
    return result;
  }
  public static char[] realloc (final char[] array, final int newSize) {
    if (newSize == 0) {
      return EMPTY_CHAR_ARRAY;
    }

    final int oldSize = array.length;
    if (oldSize == newSize) {
      return array;
    }

    final char[] result = new char[newSize];
    System.arraycopy(array, 0, result, 0, Math.min (oldSize, newSize));
    return result;
  }

  public static<T> T[] toObjectArray(Collection<T> collection, Class<T> aClass) {
    T[] array = (T[]) Array.newInstance(aClass, collection.size());
    return collection.toArray(array);
  }

  public static Object[] toObjectArray(Collection collection) {
    return toObjectArray(collection, Object.class);
  }

  public static String[] toStringArray(Collection<String> collection) {
    return toObjectArray(collection, String.class);
  }

  public static <T> T[] mergeArrays(T[] a1, T[] a2, Class<T> aClass) {
    if (a1.length == 0) {
      return a2;
    }
    if (a2.length == 0) {
      return a1;
    }
    T[] highlights =(T[])Array.newInstance(aClass, a1.length + a2.length);
    System.arraycopy(a1, 0, highlights, 0, a1.length);
    System.arraycopy(a2, 0, highlights, a1.length, a2.length);
    return highlights;
  }

  /**
   * Appends <code>element</code> to the <code>src</code> array. As you can
   * imagine the appended element will be the last one in the returned result.
   * @param src array to which the <code>element</code> should be appended.
   * @param element object to be appended to the end of <code>src</code> array.
   */
  public static <T> T[] append(@NotNull final T[] src,final T element){
    return append(src, element, (Class<T>)src.getClass().getComponentType());
  }

  public static <T> T[] append(final T[] src, final T element, final Class<T> componentType) {
    int length=src.length;
    T[] result=(T[])Array.newInstance(componentType, length+ 1);
    System.arraycopy(src,0,result,0,length);
    result[length] = element;
    return result;
  }

  /**
   * Removes element with index <code>idx</code> from array <code>src</code>.
   * @param src array.
   * @param idx index of element to be removed.
   * @return modified array.
   */
  public static <T> T[] remove(@NotNull final T[] src,int idx){
    int length=src.length;
    if (idx < 0 || idx >= length) {
      throw new IllegalArgumentException("invalid index: " + idx);
    }
    T[] result=(T[])Array.newInstance(src.getClass().getComponentType(), length-1);
    System.arraycopy(src,0,result,0,idx);
    System.arraycopy(src,idx+1,result,idx,length-idx-1);
    return result;
  }

  /**
   * @param src source array.
   * @param obj object to be found.
   * @return index of <code>obj</code> in the <code>src</code> array.
   * Returns <code>-1</code> if passed object isn't found. This method uses
   * <code>equals</code> of arrays elements to compare <code>obj</code> with
   * these elements.
   */
  public static <T> int find(@NotNull final T[] src,final T obj){
    for(int i=0;i<src.length;i++){
      final T o=src[i];
      if(o==null){
        if(obj==null){
          return i;
        }
      }else{
        if(o.equals(obj)){
          return i;
        }
      }
    }
    return -1;
  }

  public static boolean startsWith(byte[] array, byte[] subArray) {
    if (array == subArray) {
      return true;
    }
    if (array == null || subArray == null) {
      return false;
    }

    int length = subArray.length;
    if (array.length < length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (array[i] != subArray[i]) {
        return false;
      }
    }

    return true;
  }

  public static <T> boolean equals(T[] a1, T[] a2, Equality<? super T> comparator) {
    if (a1 == a2) {
      return true;
    }
    if (a1 == null || a2 == null) {
      return false;
    }

    int length = a2.length;
    if (a1.length != length) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (!comparator.equals(a1[i], a2[i])) {
        return false;
      }
    }

    return true;
  }

  public static <T> T[] reverseArray(T[] array) {
    T[] newArray = array.clone();
    for (int i = 0; i < array.length; i++) {
      newArray[array.length - i - 1] = array[i];
    }
    return newArray;
  }

  public static int lexicographicCompare(@NotNull String[] obj1, @NotNull String[] obj2) {
    for (int i = 0; i < Math.max(obj1.length, obj2.length); i++) {
      String o1 = i < obj1.length ? obj1[i] : null;
      String o2 = i < obj2.length ? obj2[i] : null;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      int res = o1.compareToIgnoreCase(o2);
      if (res != 0) return res;
    }
    return 0;
  }

  public static <T>void swap(T[] array, int i1, int i2) {
    final T t = array[i1];
    array[i1] = array[i2];
    array[i2] = t;
  }

  public static <T>void rotateLeft(T[] array, int i1, int i2) {
    final T t = array[i1];
    System.arraycopy(array, i1 + 1, array, i1, i2-i1);
    array[i2] = t;
  }
  public static <T>void rotateRight(T[] array, int i1, int i2) {
    final T t = array[i2];
    System.arraycopy(array, i1, array, i1+1, i2-i1);
    array[i1] = t;
  }
}
