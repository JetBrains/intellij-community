/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ObjectUtils");

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
        return (object != null ? object : defaultValue);
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
        if ((object1 == null) || (object2 == null)) {
            return false;
        }
        return object1.equals(object2);
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
    List expectedList = Arrays.asList(expected);
    List actualList = Arrays.asList(actual);
    return new HashSet(expectedList).equals(new HashSet(actualList)) && expected.length == actual.length;
  }

  @NotNull
  public static <T> T assertNotNull(final T t) {
    LOG.assertTrue(t != null);
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
            return ObjectUtils.NULL;
        }
    }

}
