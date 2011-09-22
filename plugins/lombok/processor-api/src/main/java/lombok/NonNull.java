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

import java.lang.annotation.*;

/**
 * Lombok is smart enough to translate any annotation named {@code @NonNull} or {@code @NotNull} in any casing and
 * with any package name to the return type of generated getters and the parameter of generated setters and constructors,
 * as well as generate the appropriate null checks in the setter and constructor.
 * <p/>
 * You can use this annotation for the purpose, though you can also use JSR305's annotation, findbugs's, pmd's, or IDEA's, or just
 * about anyone elses. As long as it is named {@code @NonNull} or {@code @NotNull}.
 * <p/>
 * WARNING: If the java community ever does decide on supporting a single {@code @NonNull} annotation (for example via JSR305), then
 * this annotation will <strong>be deleted</strong> from the lombok package. If the need to update an import statement scares
 * you, you should use your own annotation named {@code @NonNull} instead of this one.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface NonNull {
}
