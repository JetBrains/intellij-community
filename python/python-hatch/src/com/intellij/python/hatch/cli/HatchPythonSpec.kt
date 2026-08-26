// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.openapi.util.NlsSafe

/**
 * A Python implementation a Hatch `python` option can name.
 *
 * [isCPython] answers the only question a caller that offers system Pythons has: can one of them satisfy the option?
 * A system Python list holds CPython, so [PYPY] and [GRAALPY] rule every entry out.
 */
enum class HatchPythonImplementation(val isCPython: Boolean) {
  /** The option names no implementation, or names `py` or `python`, which Hatch reads as "any implementation". */
  ANY(isCPython = true),
  CPYTHON(isCPython = true),
  PYPY(isCPython = false),
  GRAALPY(isCPython = false),
  ;

  internal companion object {
    /**
     * The names Hatch reads, from `python_discovery._py_spec`. It maps `py` and `python` to "any implementation", and
     * it maps the older `graalvm` to `graalpy`.
     */
    private val BY_NAME: Map<String, HatchPythonImplementation> = mapOf(
      "py" to ANY,
      "python" to ANY,
      "cpython" to CPYTHON,
      "pypy" to PYPY,
      "graalpy" to GRAALPY,
      "graalvm" to GRAALPY,
    )

    /** The implementation [name] stands for, or null when Hatch knows no implementation by that name. */
    fun parseOrNull(name: String): HatchPythonImplementation? = BY_NAME[name.lowercase()]
  }
}

/**
 * The version part of a Hatch `python` option. A part it does not name is null, which Hatch reads as "any".
 *
 * Build one with [parseOrNull] rather than by hand, so the compact form Hatch accepts stays in one place.
 */
data class HatchPythonVersion(val major: Int, val minor: Int? = null, val micro: Int? = null) {
  /** The dotted form, holding the parts the option named: `3`, `3.11` or `3.11.2`. */
  override fun toString(): @NlsSafe String = listOfNotNull(major, minor, micro).joinToString(".")

  internal companion object {
    /** The most parts Hatch reads, from `python_discovery._py_spec._MAX_VERSION_PARTS`. */
    private const val MAX_PARTS: Int = 3

    /** The largest single group of digits Hatch reads as a major version alone, from `_SINGLE_DIGIT_MAX`. */
    private const val SINGLE_DIGIT_MAX: Int = 9

    /**
     * The version [text] names, or null when it names none. A port of `python_discovery._parse_version_parts`.
     *
     * A single group of digits above [SINGLE_DIGIT_MAX] carries the minor version in its remaining digits, which is how
     * Hatch reads `312` as 3.12 and `39` as 3.9. More than [MAX_PARTS] parts is not a version.
     */
    fun parseOrNull(text: String): HatchPythonVersion? {
      val parts = text.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
      return when {
        parts.isEmpty() || parts.size > MAX_PARTS -> null
        parts.size == MAX_PARTS -> HatchPythonVersion(parts[0], parts[1], parts[2])
        parts.size == 2 -> HatchPythonVersion(parts[0], parts[1])
        parts[0] <= SINGLE_DIGIT_MAX -> HatchPythonVersion(parts[0])
        // The compact form: the first digit is the major version and the rest is the minor one.
        else -> HatchPythonVersion(parts[0].toString().take(1).toInt(), parts[0].toString().drop(1).toInt())
      }
    }
  }
}

/**
 * The `python` option of a Hatch environment: which interpreter Hatch builds that environment from.
 *
 * A port of `python_discovery.PythonSpec.from_string_spec`, the parser Hatch itself uses for this option. It reads
 * three forms, one per implementation of this interface, and [parse] never fails because the third is also the fallback
 * for a form the other two cannot read.
 *
 * [versionSpecifiers] is what a caller that offers base interpreters needs. Everything else is here because the option
 * says it, so that a reader can see what the option constrained and what this ignored.
 */
sealed interface HatchPythonSpec {
  /** The implementation the option allows. */
  val implementation: HatchPythonImplementation

  /**
   * The option as a version specifier for `PyVersionSpecifiers`, or null when it constrains no version that specifier
   * can express.
   *
   * Null means "no constraint": a caller then offers the interpreters it would offer for an environment that names no
   * Python at all. It is deliberate for a non-CPython implementation, because no system Python satisfies one, and a
   * version taken out of such an option would name interpreters the environment rejects.
   */
  val versionSpecifiers: @NlsSafe String?

  /**
   * The `[implementation][version][t][-architecture][-isa]` form, such as `3.11`, `312`, `python3.10`, `pypy3.10` or
   * `3.13t-64`.
   */
  data class Version(
    override val implementation: HatchPythonImplementation,
    /** The version the option names, or null when it names only an implementation. */
    val version: HatchPythonVersion?,
    /** Whether the option asks for a free-threaded build, which its trailing `t` marks. */
    val freeThreaded: Boolean,
    /** The pointer-size bitness the option asks for, 32 or 64, or null for any. */
    val architecture: Int?,
    /** The instruction set the option asks for, such as `arm64`, or null for any. */
    val isa: @NlsSafe String?,
  ) : HatchPythonSpec {
    /**
     * An `==` specifier for [version], which admits any part the version leaves out.
     *
     * [freeThreaded], [architecture] and [isa] do not remove the constraint. Each narrows the choice inside one
     * version, and a caller's own list of interpreters still tells those builds apart, so keeping the version is
     * better than dropping it.
     */
    override val versionSpecifiers: @NlsSafe String?
      get() = version?.takeIf { implementation.isCPython }?.let { "==$it" }
  }

  /** The `[implementation]<specifier set>` form, such as `>=3.8`, `>=3.9,<3.13` or `python~=3.11`. */
  data class Specifiers(
    override val implementation: HatchPythonImplementation,
    /** The specifier set itself, in the form `PyVersionSpecifiers` reads. */
    val specifiers: @NlsSafe String,
  ) : HatchPythonSpec {
    override val versionSpecifiers: @NlsSafe String?
      get() = specifiers.takeIf { implementation.isCPython }
  }

  /**
   * One interpreter the option names outright, by path.
   *
   * This is also what Hatch falls back to for an option neither other form reads, so [path] is not always a path. Both
   * cases constrain no version, so they need no separate answer.
   */
  data class Interpreter(val path: @NlsSafe String) : HatchPythonSpec {
    override val implementation: HatchPythonImplementation get() = HatchPythonImplementation.ANY

    override val versionSpecifiers: @NlsSafe String? get() = null
  }

  companion object {
    /**
     * Hatch's version form. It reads no path, because neither the implementation part nor the version part accepts a
     * separator, so a path falls through to [Interpreter] as it does in Hatch.
     */
    private val VERSION_REGEX =
      """(?<impl>[a-zA-Z]+)?(?<version>[0-9.]+)?(?<threaded>t)?(?:-(?<arch>32|64))?(?:-(?<isa>[a-zA-Z0-9_.]+))?""".toRegex()

    /** Hatch's specifier form. No string matches this and [VERSION_REGEX] both, since neither part accepts an operator. */
    private val SPECIFIERS_REGEX =
      """(?:(?<impl>[A-Za-z]+)\s*)?(?<specifiers>(?:===|==|~=|!=|<=|>=|<|>).+)""".toRegex()

    /**
     * The [option] parsed.
     *
     * It falls back to [Interpreter] rather than failing, which is what Hatch does: an option it cannot read as a
     * version or a specifier set is an interpreter to look for as it stands.
     */
    fun parse(option: String): HatchPythonSpec {
      val text = option.trim()
      asVersion(text)?.let { return it }
      asSpecifiers(text)?.let { return it }
      return Interpreter(text)
    }

    /** [text] as a [Version], or null when it is not that form, its implementation is unknown, or its version is not one. */
    private fun asVersion(text: String): Version? {
      val match = VERSION_REGEX.matchEntire(text) ?: return null
      val implementation = match.implementationOrNull() ?: return null
      val versionText = match.groups["version"]?.value
      // Hatch reads a version part it cannot parse as no spec at all, and looks for the whole option as an interpreter.
      val version = versionText?.let { HatchPythonVersion.parseOrNull(it) ?: return null }
      return Version(
        implementation = implementation,
        version = version,
        freeThreaded = match.groups["threaded"] != null,
        architecture = match.groups["arch"]?.value?.toIntOrNull(),
        isa = match.groups["isa"]?.value,
      )
    }

    /** [text] as a [Specifiers], or null when it is not that form or its implementation is unknown. */
    private fun asSpecifiers(text: String): Specifiers? {
      val match = SPECIFIERS_REGEX.matchEntire(text) ?: return null
      val implementation = match.implementationOrNull() ?: return null
      val specifiers = match.groups["specifiers"]?.value ?: return null
      return Specifiers(implementation, specifiers)
    }

    /**
     * The implementation this match names, [HatchPythonImplementation.ANY] when it names none, or null when the name is
     * one Hatch does not know.
     *
     * An unknown name is not an implementation, so the whole option is not this form. Hatch reaches the same answer by
     * keeping the name and never matching an interpreter against it.
     */
    private fun MatchResult.implementationOrNull(): HatchPythonImplementation? {
      val name = groups["impl"]?.value ?: return HatchPythonImplementation.ANY
      return HatchPythonImplementation.parseOrNull(name)
    }
  }
}
