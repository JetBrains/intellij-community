/*
 * Copyright (C) 2009-2017 The Project Lombok Authors.
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
 * <p>
 * &#64;SneakyThrow does not silently swallow, wrap into RuntimeException, or otherwise modify any exceptions of the listed
 * checked exception types. The JVM does not check for the consistency of the checked exception system; javac does,
 * and this annotation lets you opt out of its mechanism.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/SneakyThrows">the project lombok features page for &#64;SneakyThrows</a>.
 * <p>
 * Example:
 * <pre>
 * &#64;SneakyThrows(UnsupportedEncodingException.class)
 * public void utf8ToString(byte[] bytes) {
 *     return new String(bytes, "UTF-8");
 * }
 * </pre>
 * 
 * Becomes:
 * <pre>
 * public void utf8ToString(byte[] bytes) {
 *     try {
 *         return new String(bytes, "UTF-8");
 *     } catch (UnsupportedEncodingException $uniqueName) {
 *         throw useMagicTrickeryToHideThisFromTheCompiler($uniqueName);
 *         // This trickery involves a bytecode transformer run automatically during the final stages of compilation;
 *         // there is no runtime dependency on lombok.
 *     }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.SOURCE)
public @interface SneakyThrows {
	/** @return The exception type(s) you want to sneakily throw onward. */
	Class<? extends Throwable>[] value() default java.lang.Throwable.class;
	
	//The fully qualified name is used for java.lang.Throwable in the parameter only. This works around a bug in javac:
	//   presence of an annotation processor throws off the type resolver for some reason.
}
