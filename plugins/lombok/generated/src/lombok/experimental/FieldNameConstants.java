/*
 * Copyright (C) 2014-2018 The Project Lombok Authors.
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

import lombok.AccessLevel;

/**
 * Generates an inner type, containing String constants containing the field name for each field. Alternatively, generates an inner enum with enum values matching each field name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface FieldNameConstants {
	lombok.AccessLevel level() default AccessLevel.PUBLIC;
	boolean asEnum() default false;
	String innerTypeName() default "";
	
	/**
	 * Only include fields and methods explicitly marked with {@code @FieldNameConstants.Include}.
	 * Normally, all (non-static) fields are included by default.
	 * 
	 * @return If {@code true}, don't include non-static fields automatically (default: {@code false}).
	 */
	boolean onlyExplicitlyIncluded() default false;
	
	/**
	 * If present, do not include this field in the generated fieldnames inner type.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.SOURCE)
	public @interface Exclude {}
	
	/**
	 * If present, include this field in the generated fieldnames inner type (default).
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.SOURCE)
	public @interface Include {}
}
