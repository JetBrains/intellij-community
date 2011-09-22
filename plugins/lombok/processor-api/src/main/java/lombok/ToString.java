/*
 * Copyright © 2009-2010 Reinier Zwitserloot and Roel Spilker.
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
package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates an implementation for the {@code toString} method inherited by all objects.
 * <p/>
 * If the method already exists, then {@code ToString} will not generate any method, and instead warns
 * that it's doing nothing at all. The parameter list and return type are not relevant when deciding to skip generation of
 * the method; any method named {@code toString} will make {@code ToString} not generate anything.
 * <p/>
 * By default, all fields that are non-static are used in the toString generation. You can exclude fields by specifying them
 * in the {@code exclude} parameter. You can also explicitly specify the fields that
 * are to be used by specifying them in the {@code of} parameter.
 * <p/>
 * Array fields are handled by way of {@link java.util.Arrays#deepToString(Object[])} where necessary.
 * The downside is that arrays with circular references (arrays that contain themselves,
 * possibly indirectly) results in calls to {@code toString} throwing a
 * {@link StackOverflowError}. However, the implementations for java's own {@link java.util.ArrayList} suffer
 * from the same flaw.
 * <p/>
 * The {@code toString} method that is generated will print the class name as well as each field (both the name
 * and the value). You can optionally choose to suppress the printing of the field name, by setting the
 * {@code includeFieldNames} flag to <em>false</em>.
 * <p/>
 * You can also choose to include the result of {@code toString} in your class's superclass by setting the
 * {@code callSuper} to <em>true</em>.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ToString {
  /**
   * Include the name of each field when printing it.
   * <strong>default: true</strong>
   */
  boolean includeFieldNames() default true;

  /**
   * Any fields listed here will not be printed in the generated {@code toString} implementation.
   * Mutually exclusive with {@link #of()}.
   */
  String[] exclude() default {};

  /**
   * If present, explicitly lists the fields that are to be printed.
   * Normally, all non-static fields are printed.
   * <p/>
   * Mutually exclusive with {@link #exclude()}.
   */
  String[] of() default {};

  /**
   * Include the result of the superclass's implementation of {@code toString} in the output.
   * <strong>default: false</strong>
   */
  boolean callSuper() default false;

  /**
   * Normally, if getters are available, those are called. To suppress this and let the generated code use the fields directly, set this to {@code true}.
   * <strong>default: false</strong>
   */
  boolean doNotUseGetters() default false;
}
