/*
 * Copyright © 2010-2012 Philipp Eichhorn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.plushnikov.intellij.plugin.thirdparty;

import java.lang.annotation.Annotation;

/**
 * Error messages from lombok_pg project
 *
 * @author Philipp Eichhorn
 * @see https://github.com/peichhorn/lombok-pg/blob/master/src/core/lombok/core/util/ErrorMessages.java
 */
public final class ErrorMessages {

  public static String canBeUsedOnConcreteClassOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on concrete classes only", annotationType);
  }

  public static String canBeUsedOnClassOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on classes only", annotationType);
  }

  public static String canBeUsedOnClassAndEnumOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on classes and enums only", annotationType);
  }

  public static String canBeUsedOnClassAndFieldOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on classes and fields only", annotationType);
  }

  public static String canBeUsedOnClassAndMethodOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on classes and methods only", annotationType);
  }

  public static String canBeUsedOnFieldOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on fields only", annotationType);
  }

  public static String canBeUsedOnPrivateFinalFieldOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on private final fields only", annotationType);
  }

  public static String canBeUsedOnInitializedFieldOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on initialized fields only", annotationType);
  }

  public static String canBeUsedOnMethodOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on methods only", annotationType);
  }

  public static String canBeUsedOnStaticMethodOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on static methods only", annotationType);
  }

  public static String canBeUsedOnConcreteMethodOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on concrete methods only", annotationType);
  }

  public static String canBeUsedOnEnumFieldsOnly(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s can be used on enum fields only", annotationType);
  }

  public static String requiresDefaultOrNoArgumentConstructor(final Class<? extends Annotation> annotationType) {
    return errorMessage("@%s requires a default or no-argument constructor", annotationType);
  }

  public static String unsupportedExpressionIn(final String where, final Object expr) {
    return String.format("Unsupported Expression in '%s': %s", where, expr);
  }

  public static String isNotAllowedHere(final String what) {
    return String.format("'%s' is not allowed here.", what);
  }

  public static String firstArgumentCanBeVariableNameOrNewClassStatementOnly(final String what) {
    return String.format("The first argument of '%s' can be variable name or new-class statement only", what);
  }

  public static String canBeUsedInBodyOfMethodsOnly(final String what) {
    return String.format("'%s' can be used in the body of methods only", what);
  }

  private static String errorMessage(final String format, final Class<? extends Annotation> annotationType) {
    return String.format(format, annotationType.getName());
  }
}
