/*
 * Copyright (C) 2010-2017 The Project Lombok Authors.
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
package lombok.extern.java;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes lombok to generate a logger field.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/Log">the project lombok features page for lombok log annotations</a>.
 * <p>
 * Example:
 * <pre>
 * &#64;Log
 * public class LogExample {
 * }
 * </pre>
 * 
 * will generate:
 * 
 * <pre>
 * public class LogExample {
 *     private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogExample.class.getName());
 * }
 * </pre>
 * 
 * This annotation is valid for classes and enumerations.<br>
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/util/logging/Logger.html">java.util.logging.Logger</a>
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/util/logging/Logger.html#getLogger-java.lang.String-">java.util.logging.Logger#getLogger(java.lang.String)</a>
 * @see lombok.extern.apachecommons.CommonsLog &#64;CommonsLog
 * @see lombok.extern.log4j.Log4j &#64;Log4j
 * @see lombok.extern.log4j.Log4j2 &#64;Log4j2
 * @see lombok.extern.slf4j.Slf4j &#64;Slf4j
 * @see lombok.extern.slf4j.XSlf4j &#64;XSlf4j
 * @see lombok.extern.jbosslog.JBossLog &#64;JBossLog
 * @see lombok.extern.flogger.Flogger &#64;Flogger
 * @see lombok.CustomLog &#64;CustomLog
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Log {
	/** @return The category of the constructed Logger. By default, it will use the type where the annotation is placed. */
	String topic() default "";
}
