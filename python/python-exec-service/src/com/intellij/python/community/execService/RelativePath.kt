// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * A path relative to a directory. It has one or more [parts], for example `["config.d", "file.cfg"]`.
 */
class RelativePath(internal vararg val parts: String) {
  init {
    require(parts.isNotEmpty()) { "A relative path must have one or more parts" }
  }
}

/**
 * If `this` is `[foo, bar]` and [root] is `/etc`, this function returns `/etc/foo/bar`.
 */
internal fun RelativePath.resolveAgainst(root: Directory): Path = parts.fold(root, Path::resolve)

/**
 * A small DSL to make a [RelativePath] with two or more parts:
 * ```kotlin
 * RelativePath { "config.d" / "sub" / "file.cfg" }
 * ```
 * For a path with one part, use the constructor: `RelativePath("file.cfg")`.
 */
fun RelativePath(path: RelativePathBuilder.() -> Unit): RelativePath {
  val builder = RelativePathBuilderImpl()
  path.invoke(builder)
  return RelativePath(*builder.items.toTypedArray())
}


/**
 * The DSL for [RelativePath].
 */
@ApiStatus.NonExtendable
sealed interface RelativePathBuilder {
  operator fun String.div(other: String): RelativePathBuilder
  operator fun RelativePathBuilder.div(other: String): RelativePathBuilder
}

private class RelativePathBuilderImpl : RelativePathBuilder {
  private val _items = mutableListOf<String>()
  val items: List<String> = _items
  override fun String.div(other: String): RelativePathBuilder {
    _items.add(this)
    _items.add(other)
    return this@RelativePathBuilderImpl
  }

  override fun RelativePathBuilder.div(other: String): RelativePathBuilder {
    _items.add(other)
    return this
  }
}


