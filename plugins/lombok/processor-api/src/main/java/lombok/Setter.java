/*
 * Copyright © 2009-2010 Reinier Zwitserloot, Roel Spilker and Robbert Jan Grootjans.
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
 * Put on any field to make lombok build a standard setter.
 * <p/>
 * Example:
 * <pre>
 *     private &#64;Setter int foo;
 * </pre>
 * <p/>
 * will generate:
 * <p/>
 * <pre>
 *     public void setFoo(int foo) {
 *         this.foo = foo;
 *     }
 * </pre>
 * <p/>
 * If any method named {@code setFoo} exists, regardless of return type or parameters, no method is generated,
 * and instead a compiler warning is emitted.
 * <p/>
 * This annotation can also be applied to a class, in which case it'll be as if all non-static fields that don't already have
 * a {@code Setter} annotation have the annotation.
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Setter {
  /**
   * If you want your setter to be non-public, you can specify an alternate access level here.
   */
  AccessLevel value() default AccessLevel.PUBLIC;
}