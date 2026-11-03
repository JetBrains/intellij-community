// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts the tag of the annotated child after the tag that {@link #value()} names.
 * <p>
 * The DOM uses the order when it creates the tag of the child. It inserts the new tag after the last anchor tag,
 * and it appends the new tag when the parent holds no anchor tag. The order of the existing tags does not change.
 * <p>
 * One annotation is enough for a pair, because the DOM inverts the relation. {@code @WriteAfter("groupId")} on
 * {@code getArtifactId} keeps a new {@code artifactId} after {@code groupId}, and a new {@code groupId} before
 * {@code artifactId}. Use {@link WriteBefore} when the other child is not yours to annotate.
 * <p>
 * Example:
 * <pre>
 *   GenericDomValue&lt;String&gt; getGroupId();
 *
 *   &#64;WriteAfter("groupId")
 *   GenericDomValue&lt;String&gt; getArtifactId();
 * </pre>
 * The tags of one name stay together, so the annotation also holds for a collection child.
 * <h3>Limits</h3>
 * <ul>
 *   <li>The DOM applies the order only when it creates the tag. It never moves a tag that the file already holds.
 *   <li>A write that goes to the PSI directly keeps no order.
 *   <li>The DOM reads the annotations of the static type. It ignores an order that a {@code DomExtender} declares.
 *   <li>The annotation holds for a tag only. An attribute keeps no order.
 *   <li>A contradictory pair gives the {@link WriteAfter} anchor the priority, and the DOM reports nothing.
 *   <li>A value that names no child of the parent has no effect, and the DOM reports nothing.
 * </ul>
 *
 * @see WriteBefore
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(WriteAfter.List.class)
public @interface WriteAfter {
  /** The local name of the anchor tag. It must be a child tag name of the same parent. */
  @NonNls String value();

  /** Holds the repeated {@link WriteAfter} annotations of one child. */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    WriteAfter[] value();
  }
}

