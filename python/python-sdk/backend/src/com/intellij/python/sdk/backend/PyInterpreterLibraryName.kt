// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.python.sdk.common.PyInterpreterRef
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * The interpreter of one Python project, as its module stores it: the name of a project library that the module
 * depends on.
 *
 * The module stores it in its own `.iml`, as `<orderEntry type="library" name="<name>" level="project"/>`, so the
 * entry belongs to the module, and a rebuild of the module keeps it. The library itself, with the roots of the
 * interpreter, is not stored: it is built from this name when the project loads, so no local path reaches `.idea`.
 *
 * A ref means something only in the base directory of the Python project that owns the interpreter, and two Python
 * projects can have the same ref for two interpreters. So the name holds [ownerDir], the directory the ref is
 * resolved against. A module whose interpreter belongs to another Python project, such as a member of a tool workspace
 * that uses the interpreter of the workspace root, names the directory of that owner. The two modules then share one
 * library, as they share the interpreter.
 *
 * The name is `Python: <ownerDir> <ref>`, for example `Python: . native:uv:.venv` or `Python: libs/foo native:uv:.venv`.
 * The first space ends [ownerDir]: its text holds no raw space, see [PyInterpreterOwnerDir]. The ref is the rest, as
 * it is, so a space, a bracket or a `:` in it changes nothing. The prefix follows the form of other generated
 * libraries, such as `Maven: ...`, and only these names parse, so a library of the user is never taken for an
 * interpreter.
 */
@ApiStatus.Internal
data class PyInterpreterLibraryName(val ownerDir: PyInterpreterOwnerDir, val ref: PyInterpreterRef) {
  override fun toString(): String = "$PREFIX$ownerDir$SEPARATOR$ref"

  companion object {
    /** The prefix of every name. It is stored in `.iml` files, so it is never localized and never changes. */
    private const val PREFIX = "Python: "
    private const val SEPARATOR = " "

    /** The name of the library of the interpreter of [key] in the project at [projectDir]. */
    fun of(projectDir: Path, key: PythonInterpreterKey): PyInterpreterLibraryName =
      PyInterpreterLibraryName(PyInterpreterOwnerDir.of(projectDir, key.ownerDir), key.ref)

    /** The name that [value] states, or `null` when [value] is the name of another library. */
    fun parse(value: String): PyInterpreterLibraryName? {
      if (!value.startsWith(PREFIX)) return null
      val rest = value.substring(PREFIX.length)
      val separator = rest.indexOf(SEPARATOR)
      if (separator < 0) return null
      val dir = PyInterpreterOwnerDir.parse(rest.substring(0, separator)) ?: return null
      val ref = PyInterpreterRef.parse(rest.substring(separator + SEPARATOR.length)) ?: return null
      return PyInterpreterLibraryName(dir, ref)
    }
  }
}

/**
 * The base directory of the Python project that owns an interpreter: the directory a [PyInterpreterRef] is resolved
 * against.
 *
 * It holds the text as the `.iml` stores it. Most often it is relative to the project directory, so two names that
 * state one directory are equal on every machine: `/` is the separator on every OS, [ROOT] is the project directory
 * itself, and a module outside the project directory, such as an attached project, starts with `..` segments.
 *
 * A directory that no relative path reaches, such as a module on another drive of Windows or in another file system,
 * is absolute: its text starts with [ABSOLUTE_MARK], for example `@D:/lib`. Such a name holds a local path, as an
 * `.iml` does for a content root on another drive.
 *
 * The text holds no raw space, and no raw [ABSOLUTE_MARK] after its start: those characters and `%` are percent-encoded,
 * see [encode]. So the first space of a name ends the directory, and a relative directory never reads as an absolute one.
 */
@ApiStatus.Internal
@JvmInline
value class PyInterpreterOwnerDir private constructor(private val value: String) {
  /** This directory in the project at [projectDir]. An absolute directory is itself. */
  fun resolveIn(projectDir: Path): Path =
    if (value.startsWith(ABSOLUTE_MARK)) Path.of(decode(value.substring(ABSOLUTE_MARK.length))).normalize()
    else projectDir.resolve(decode(value)).normalize()

  override fun toString(): String = value

  companion object {
    /** The project directory itself. */
    private const val ROOT_TEXT = "."

    /** The start of the text of an absolute directory. */
    private const val ABSOLUTE_MARK = "@"

    /** The characters that the text percent-encodes: the one that encodes, the separator of a name, and the mark. */
    private val ENCODED = mapOf('%' to "%25", ' ' to "%20", '@' to "%40")

    val ROOT: PyInterpreterOwnerDir = PyInterpreterOwnerDir(ROOT_TEXT)

    /**
     * The directory of [ownerBaseDir] for the project at [projectDir]: relative to it when a relative path leads there,
     * else absolute.
     */
    fun of(projectDir: Path, ownerBaseDir: Path): PyInterpreterOwnerDir {
      // The two paths have no common root: different drives of Windows, or different file systems.
      val relative = try {
        projectDir.normalize().relativize(ownerBaseDir.normalize())
      }
      catch (_: IllegalArgumentException) {
        null
      }
      if (relative == null) {
        return PyInterpreterOwnerDir(ABSOLUTE_MARK + encode(ownerBaseDir.normalize().invariantSeparatorsPathString))
      }
      val text = relative.invariantSeparatorsPathString
      return if (text.isEmpty()) ROOT else PyInterpreterOwnerDir(encode(text))
    }

    /**
     * The directory [value] states, or `null` when it is not the text of a directory: it holds a raw space, a raw
     * [ABSOLUTE_MARK] after its start, or a bad escape. A relative directory is without `:`, and a `..` segment may only
     * lead it, so each directory has one text.
     */
    fun parse(value: String): PyInterpreterOwnerDir? {
      if (value.isEmpty() || ' ' in value) return null
      if (value.startsWith(ABSOLUTE_MARK)) {
        val path = value.substring(ABSOLUTE_MARK.length)
        if (path.isEmpty() || ABSOLUTE_MARK in path || decodeOrNull(path) == null) return null
        return PyInterpreterOwnerDir(value)
      }
      if (ABSOLUTE_MARK in value || ':' in value || value.startsWith("/")) return null
      val decoded = decodeOrNull(value) ?: return null
      if (decoded.split('/').dropWhile { it == ".." }.any { it == ".." || it == "." && value != ROOT_TEXT }) return null
      return PyInterpreterOwnerDir(value)
    }

    private fun encode(text: String): String = buildString {
      for (char in text) append(ENCODED[char] ?: char)
    }

    private fun decode(text: String): String = checkNotNull(decodeOrNull(text)) { "Not an encoded directory: $text" }

    /** [text] with each escape of [ENCODED] replaced by its character, or `null` when an escape is not one of them. */
    private fun decodeOrNull(text: String): String? {
      val decoded = ENCODED.entries.associate { (char, escape) -> escape to char }
      return buildString {
        var i = 0
        while (i < text.length) {
          if (text[i] == '%') {
            append(decoded[text.substring(i, minOf(i + 3, text.length))] ?: return null)
            i += 3
          }
          else {
            append(text[i])
            i++
          }
        }
      }
    }
  }
}

/**
 * Replaces the interpreter entry of these dependencies with [name], or removes it for a `null` [name]. Any other
 * dependency keeps its place. A new entry goes last, after the sources of the module and its other dependencies.
 */
@ApiStatus.Internal
fun MutableList<ModuleDependencyItem>.setPyInterpreterDependency(name: PyInterpreterLibraryName?) {
  val current = pyInterpreterDependency
  val wanted = name?.let { LibraryDependency(LibraryId(it.toString(), LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE) }
  if (current == wanted) return
  val index = current?.let(::indexOf) ?: -1
  if (index >= 0) removeAt(index)
  if (wanted != null) add(if (index >= 0) index else size, wanted)
}

/** The interpreter entry among these dependencies: the first project library whose name parses, or `null`. */
@get:ApiStatus.Internal
val Iterable<ModuleDependencyItem>.pyInterpreterDependency: LibraryDependency?
  get() = firstNotNullOfOrNull { item ->
    (item as? LibraryDependency)?.takeIf { it.library.tableId == LibraryTableId.ProjectLibraryTableId && PyInterpreterLibraryName.parse(it.library.name) != null }
  }
