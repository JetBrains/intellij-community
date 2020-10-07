/*
 * Copyright (C) 2009-2018 The Project Lombok Authors.
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
 * Generates an implementation for the {@code toString} method inherited by all objects, consisting of printing the values of relevant fields.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/ToString">the project lombok features page for &#64;ToString</a>.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ToString {
	/**
	 * Include the name of each field when printing it.
	 * <strong>default: true</strong>
	 * 
	 * @return Whether or not to include the names of fields in the string produced by the generated {@code toString()}.
	 */
	boolean includeFieldNames() default true;
	
	/**
	 * Any fields listed here will not be printed in the generated {@code toString} implementation.
	 * Mutually exclusive with {@link #of()}.
	 * <p>
	 * Will soon be marked {@code @Deprecated}; use the {@code @ToString.Exclude} annotation instead.
	 * 
	 * @return A list of fields to exclude.
	 */
	String[] exclude() default {};
	
	/**
	 * If present, explicitly lists the fields that are to be printed.
	 * Normally, all non-static fields are printed.
	 * <p>
	 * Mutually exclusive with {@link #exclude()}.
	 * <p>
	 * Will soon be marked {@code @Deprecated}; use the {@code @ToString.Include} annotation together with {@code @ToString(onlyExplicitlyIncluded = true)}.
	 * 
	 * @return A list of fields to use (<em>default</em>: all of them).
	 */
	String[] of() default {};
	
	/**
	 * Include the result of the superclass's implementation of {@code toString} in the output.
	 * <strong>default: false</strong>
	 * 
	 * @return Whether to call the superclass's {@code toString} implementation as part of the generated toString algorithm.
	 */
	boolean callSuper() default false;
	
	/**
	 * Normally, if getters are available, those are called. To suppress this and let the generated code use the fields directly, set this to {@code true}.
	 * <strong>default: false</strong>
	 * 
	 * @return If {@code true}, always use direct field access instead of calling the getter method.
	 */
	boolean doNotUseGetters() default false;
	
	/**
	 * Only include fields and methods explicitly marked with {@code @ToString.Include}.
	 * Normally, all (non-static) fields are included by default.
	 * 
	 * @return If {@code true}, don't include non-static fields automatically (default: {@code false}).
	 */
	boolean onlyExplicitlyIncluded() default false;
	
	/**
	 * If present, do not include this field in the generated {@code toString}.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.SOURCE)
	public @interface Exclude {}
	
	/**
	 * Configure the behaviour of how this member is rendered in the {@code toString}; if on a method, include the method's return value in the output.
	 */
	@Target({ElementType.FIELD, ElementType.METHOD})
	@Retention(RetentionPolicy.SOURCE)
	public @interface Include {
//		/** If true and the return value is {@code null}, omit this member entirely from the {@code toString} output. */
//		boolean skipNull() default false; // -- We'll add it later, it requires a complete rework on the toString code we generate.
		
		/**
		 * Higher ranks are printed first. Members of the same rank are printed in the order they appear in the source file.
		 * 
		 * @return ordering within the generating {@code toString()}; higher numbers are printed first.
		 */
		int rank() default 0;
		
		/**
		 * Defaults to the field / method name of the annotated member.
		 * If the name equals the name of a default-included field, this member takes its place.
		 * 
		 * @return The name to show in the generated {@code toString()}. Also, if this annotation is on a method and the name matches an existing field, it replaces that field.
		 */
		String name() default "";
	}
}
