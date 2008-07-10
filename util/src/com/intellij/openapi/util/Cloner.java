/*
 * @author max
 */
package com.intellij.openapi.util;

public interface Cloner<T> {
    T cloneOf(T t);
    T copyOf(T t);
}