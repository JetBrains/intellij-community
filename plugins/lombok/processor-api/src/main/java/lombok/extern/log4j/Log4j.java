/*
 * Copyright © 2010 Reinier Zwitserloot, Roel Spilker and Robbert Jan Grootjans.
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
package lombok.extern.log4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes lombok to generate a logger field.
 * Example:
 * <pre>
 * &#64;Log4j
 * public class LogExample {
 * }
 * </pre>
 * <p/>
 * will generate:
 * <p/>
 * <pre>
 * public class LogExample {
 *     private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LogExample.class);
 * }
 * </pre>
 * <p/>
 * This annotation is valid for classes and enumerations.<br />
 *
 * @see org.apache.log4j.Logger org.apache.log4j.Logger
 * @see org.apache.log4j.Logger#getLogger(Class) org.apache.log4j.Logger.getLogger(Class target)
 * @see lombok.extern.apachecommons.CommonsLog &#64;CommonsLog
 * @see lombok.extern.java.Log &#64;Log
 * @see lombok.extern.slf4j.Slf4j &#64;Slf4j
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Log4j {
}