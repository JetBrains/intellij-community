/*
 * Copyright © 2010-2011 Reinier Zwitserloot, Roel Spilker and Robbert Jan Grootjans.
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
 * Generates an all-args constructor. An all-args constructor requires one argument
 * for every field in the class.
 *
 * @see NoArgsConstructor
 * @see RequiredArgsConstructor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AllArgsConstructor {
  /**
   * If set, the generated constructor will be private, and an additional static 'constructor'
   * is generated with the same argument list that wraps the real constructor.
   * <p/>
   * Such a static 'constructor' is primarily useful as it infers type arguments.
   */
  String staticName() default "";

  /**
   * Sets the access level of the constructor. By default, generated constructors are {@code public}.
   */
  AccessLevel access() default AccessLevel.PUBLIC;

  /**
   * Constructors are generated with the {@link java.beans.ConstructorProperties} annotation.
   * However, this annotation is new in 1.6 which means those compiling for 1.5 will need
   * to set this value to true.
   *
   * @deprecated Java 1.5 has already been end-of-lifed. As java 1.5 loses ground this
   *             method will eventually be removed.
   */
  @Deprecated boolean suppressConstructorProperties() default false;
}
