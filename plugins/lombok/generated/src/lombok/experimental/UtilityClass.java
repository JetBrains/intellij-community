/*
 * Copyright (C) 2015-2019 The Project Lombok Authors.
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
package lombok.experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to create utility classes.
 * 
 * If a class is annotated with {@code @UtilityClass}, the following things happen to it:<ul>
 * <li>It is marked final.</li>
 * <li>If any constructors are declared in it, an error is generated. Otherwise, a private no-args constructor is generated; it throws a {@code UnsupportedOperationException}.</li>
 * <li>All methods, inner classes, and fields in the class are marked static.</li>
 * <li><em>WARNING:</em> Do not use non-star static imports to import these members; javac won't be able to figure it out. Use either:
 *    <code>import static ThisType.*;</code> or don't static-import.</li>
 * </ul>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface UtilityClass {
}
