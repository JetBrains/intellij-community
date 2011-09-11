/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
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
 * &#64;SneakyThrow will avoid javac's insistence that you either catch or throw onward any checked exceptions that
 * statements in your method body declare they generate.
 * <p/>
 * &#64;SneakyThrow does not silently swallow, wrap into RuntimeException, or otherwise modify any exceptions of the listed
 * checked exception types. The JVM does not check for the consistency of the checked exception system; javac does,
 * and this annotation lets you opt out of its mechanism.
 * <p/>
 * You should use this annotation ONLY in the following two cases:<ol>
 * <li>You are certain the listed exception can't actually ever happen, or only in vanishingly rare situations.
 * You don't try to catch OutOfMemoryError on every statement either. Examples:<br>
 * {@code IOException} in {@code ByteArrayOutputStream}<br>
 * {@code UnsupportedEncodingException} in new String(byteArray, "UTF-8").</li>
 * <li>You know for certain the caller can handle the exception (for example, because the caller is
 * an app manager that will handle all throwables that fall out of your method the same way), but due
 * to interface restrictions you can't just add these exceptions to your 'throws' clause.
 * <p/>
 * Note that, as SneakyThrow is an implementation detail and <i>NOT</i> part of your method signature, it is
 * a compile time error if none of the statements in your method body can throw a listed exception.
 * <p/>
 * <b><i>WARNING: </b></i>You must have lombok.jar available at the runtime of your app if you use SneakyThrows,
 * because your code is rewritten to use {@link Lombok#sneakyThrow(Throwable)}.
 * <p/>
 * <p/>
 * Example:
 * <pre>
 * &#64;SneakyThrows(UnsupportedEncodingException.class)
 * public void utf8ToString(byte[] bytes) {
 *     return new String(bytes, "UTF-8");
 * }
 * </pre>
 * <p/>
 * {@code @SneakyThrows} without a parameter defaults to allowing <i>every</i> checked exception.
 * (The default is {@code Throwable.class}).
 *
 * @see Lombok#sneakyThrow(Throwable)
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.SOURCE)
public @interface SneakyThrows {
  /**
   * The exception type(s) you want to sneakily throw onward.
   */
  Class<? extends Throwable>[] value() default Throwable.class;

  //The package is mentioned in java.lang due to a bug in javac (presence of an annotation processor throws off the type resolver for some reason).
}
