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
 * Almost exactly like putting the 'synchronized' keyword on a method, except will synchronize on a private internal
 * Object, so that other code not under your control doesn't meddle with your thread management by locking on
 * your own instance.
 * <p>
 * For non-static methods, a field named {@code $lock} is used, and for static methods,
 * {@code $LOCK} is used. These will be generated if needed and if they aren't already present. The contents
 * of the fields will be serializable.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/Synchronized">the project lombok features page for &#64;Synchronized</a>.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Synchronized {
	/**
	 * Optional: specify the name of a different field to lock on. It is a compile time error if this field
	 * doesn't already exist (the fields are automatically generated only if you don't specify a specific name.
	 * 
	 * @return Name of the field to lock on (blank = generate one).
	 */
	String value() default "";
}
