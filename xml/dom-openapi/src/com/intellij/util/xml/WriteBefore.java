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
 * Puts the tag of the annotated child before the tag that {@link #value()} names.
 * <p>
 * The DOM uses the order when it creates the tag of the child. It inserts the new tag before the first anchor tag,
 * and it appends the new tag when the parent holds no anchor tag. The order of the existing tags does not change.
 * <p>
 * One annotation is enough for a pair, because the DOM inverts the relation. {@code @WriteBefore("artifactId")} on
 * {@code getGroupId} keeps a new {@code groupId} before {@code artifactId}, and a new {@code artifactId} after
 * {@code groupId}.
 * <p>
 * Example:
 * <pre>
 *   &#64;WriteBefore("artifactId")
 *   GenericDomValue&lt;String&gt; getGroupId();
 *
 *   GenericDomValue&lt;String&gt; getArtifactId();
 * </pre>
 * The tags of one name stay together, so the annotation also holds for a collection child.
 * <p>
 * The limits of {@link WriteAfter} hold also for this annotation.
 *
 * @see WriteAfter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(WriteBefore.List.class)
public @interface WriteBefore {
  /** The local name of the anchor tag. It must be a child tag name of the same parent. */
  @NonNls String value();

  /** Holds the repeated {@link WriteBefore} annotations of one child. */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    WriteBefore[] value();
  }
}

