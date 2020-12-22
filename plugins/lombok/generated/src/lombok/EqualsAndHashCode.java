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
 * Generates implementations for the {@code equals} and {@code hashCode} methods inherited by all objects, based on relevant fields.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/EqualsAndHashCode">the project lombok features page for &#64;EqualsAndHashCode</a>.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface EqualsAndHashCode {
	/**
	 * Any fields listed here will not be taken into account in the generated {@code equals} and {@code hashCode} implementations.
	 * Mutually exclusive with {@link #of()}.
	 * <p>
	 * Will soon be marked {@code @Deprecated}; use the {@code @EqualsAndHashCode.Exclude} annotation instead.
	 * 
	 * @return A list of fields to exclude.
	 */
	String[] exclude() default {};
	
	/**
	 * If present, explicitly lists the fields that are to be used for identity.
	 * Normally, all non-static, non-transient fields are used for identity.
	 * <p>
	 * Mutually exclusive with {@link #exclude()}.
	 * <p>
	 * Will soon be marked {@code @Deprecated}; use the {@code @EqualsAndHashCode.Include} annotation together with {@code @EqualsAndHashCode(onlyExplicitlyIncluded = true)}.
	 * 
	 * @return A list of fields to use (<em>default</em>: all of them).
	 */
	String[] of() default {};
	
	/**
	 * Call on the superclass's implementations of {@code equals} and {@code hashCode} before calculating for the fields in this class.
	 * <strong>default: false</strong>
	 * 
	 * @return Whether to call the superclass's {@code equals} implementation as part of the generated equals algorithm.
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
	 * Any annotations listed here are put on the generated parameter of {@code equals} and {@code canEqual}.
	 * This is useful to add for example a {@code Nullable} annotation.<br>
	 * The syntax for this feature depends on JDK version (nothing we can do about that; it's to work around javac bugs).<br>
	 * up to JDK7:<br>
	 *  {@code @EqualsAndHashCode(onParam=@__({@AnnotationsGoHere}))}<br>
	 * from JDK8:<br>
	 *  {@code @EqualsAndHashCode(onParam_={@AnnotationsGohere})} // note the underscore after {@code onParam}.
	 *  
	 * @return List of annotations to apply to the generated parameter in the {@code equals()} method.
	 */
	AnyAnnotation[] onParam() default {};
	
	/**
	 * Placeholder annotation to enable the placement of annotations on the generated code.
	 * @deprecated Don't use this annotation, ever - Read the documentation.
	 */
	@Deprecated
	@Retention(RetentionPolicy.SOURCE)
	@Target({})
	@interface AnyAnnotation {}
	
	/**
	 * Only include fields and methods explicitly marked with {@code @EqualsAndHashCode.Include}.
	 * Normally, all (non-static, non-transient) fields are included by default.
	 * 
	 * @return If {@code true}, don't include non-static non-transient fields automatically (default: {@code false}).
	 */
	boolean onlyExplicitlyIncluded() default false;
	
	/**
	 * If present, do not include this field in the generated {@code equals} and {@code hashCode} methods.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.SOURCE)
	public @interface Exclude {}
	
	/**
	 * Configure the behaviour of how this member is treated in the {@code equals} and {@code hashCode} implementation; if on a method, include the method's return value as part of calculating hashCode/equality.
	 */
	@Target({ElementType.FIELD, ElementType.METHOD})
	@Retention(RetentionPolicy.SOURCE)
	public @interface Include {
		/**
		 * Defaults to the method name of the annotated member.
		 * If on a method and the name equals the name of a default-included field, this member takes its place.
		 * 
		 * @return If present, this method serves as replacement for the named field.
		 */
		String replaces() default "";
	}
}
