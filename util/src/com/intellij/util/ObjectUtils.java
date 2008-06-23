/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Common <code>Object</code> manipulation routines.
 *
 * @author <a href="mailto:nissim@nksystems.com">Nissim Karpenstein</a>
 * @author <a href="mailto:janekdb@yahoo.co.uk">Janek Bogucki</a>
 * @author <a href="mailto:dlr@finemaltcoding.com">Daniel Rall</a>
 * @author <a href="mailto:scolebourne@joda.org">Stephen Colebourne</a>
 * @version $Id: ObjectUtils.java,v 1.4 2002/09/22 09:18:33 scolebourne Exp $
 */
public class ObjectUtils {
    /**
     * Singleton used as a null placeholder where null has another meaning.
     * <p>
     * For example, in a <code>HashMap</code> the get(key) method returns null
     * if the Map contains null or if there is no matching key. The Null
     * placeholder can be used to distinguish between these two cases.
     * <p>
     * Another example is <code>HashTable</code>, where <code>null</code>
     * cannot be stored.
     * <p>
     * This instance is Serializable.
     */
    public static final Null NULL = new Null();

    /**
     * ObjectUtils instances should NOT be constructed in standard programming.
     * Instead, the class should be used as <code>ObjectUtils.defaultIfNull("a","b");</code>.
     * This constructor is public to permit tools that require a JavaBean instance
     * to operate.
     */
    public ObjectUtils() {
    }

    //--------------------------------------------------------------------

    /**
     * Returns a default value if the object passed is null.
     *
     * @param object  the object to test
     * @param defaultValue  the default value to return
     * @return object if it is not null, defaultValue otherwise
     */
    public static Object defaultIfNull(Object object, Object defaultValue) {
        return object != null ? object : defaultValue;
    }

    /**
     * Compares two objects for equality, where either one or both
     * objects may be <code>null</code>.
     *
     * @param object1  the first object
     * @param object2  the second object
     * @return <code>true</code> if the values of both objects are the same
     */
    public static boolean equals(Object object1, Object object2) {
        if (object1 == object2) {
            return true;
        }
      return object1 != null && object2 != null && object1.equals(object2);
    }

    /**
     * Gets the toString that would be produced by Object if a class did not
     * override toString itself. Null will return null.
     *
     * @param object  the object to create a toString for, may be null
     * @return the default toString text, or null if null passed in
     */
    public static String identityToString(Object object) {
        if (object == null) {
            return null;
        }
        return new StringBuffer()
            .append(object.getClass().getName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(object)))
            .toString();
    }

  public static boolean haveSameElements(Object[] expected, Object[] actual) {
    List<Object> expectedList = Arrays.asList(expected);
    List<Object> actualList = Arrays.asList(actual);
    return new HashSet<Object>(expectedList).equals(new HashSet<Object>(actualList)) && expected.length == actual.length;
  }

  @NotNull
  public static <T> T assertNotNull(@NotNull final T t) {
    return t;
  }

  /**
     * Class used as a null placeholder where null has another meaning.
     * <p>
     * For example, in a <code>HashMap</code> the get(key) method returns null
     * if the Map contains null or if there is no matching key. The Null
     * placeholder can be used to distinguish between these two cases.
     * <p>
     * Another example is <code>HashTable</code>, where <code>null</code>
     * cannot be stored.
     */
    public static class Null implements Serializable {
        /**
         * Restricted constructor - singleton
         */
        private Null() {
        }

        /**
         * Ensure singleton.
         * @return the singleton value
         */
        private Object readResolve() {
            return NULL;
        }
    }

}
