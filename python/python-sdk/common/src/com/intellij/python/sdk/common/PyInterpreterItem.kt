// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.common

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.trimMiddle
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls

private const val ELLIPSIS = "\u2026"

/**
 * Why an interpreter is flagged, with the text to show for it.
 *
 * @property marker the short word a renderer puts in brackets before the name, such as `invalid`.
 * @property reason the full explanation, or `null` when the check that flagged the interpreter gives none.
 */
@Serializable
data class PythonInterpreterProblem(
  val marker: @Nls String,
  val reason: @Nls String?,
)

/**
 * One interpreter as a UI list holds it: what to select, and what to draw.
 *
 * Every list, tree and combo that presents interpreters holds these instead of SDKs. It is built off the EDT, because
 * deciding [problem] reads the interpreter's environment, and a renderer only reads what is here.
 *
 * **Not a data class, on purpose.** Two items are equal when they name the same interpreter, and nothing else takes
 * part: [icon] is an [IconId], whose equality is identity, so a content-wise comparison would differ between two
 * builds for the same interpreter and a combo box would fail to match its own selection. `PyInterpreterItemTest`
 * pins this.
 *
 * @property ref the ref of this interpreter, which is what selects it.
 * @property name Main label, built from [ref]: the tool that manages the environment, and where it runs when that is
 *   not this machine, such as `uv` or `Conda · SSH`. Two interpreters can have one name, so the name is no key.
 * @property suffix Trailing info, such as the version `3.12.1` or `[sudo] 3.12.1`, or `null`.
 * @property description The root of the environment, relative to the user home, such as `~/app/.venv`.
 * @property problem Why the interpreter is flagged, or `null` when it is usable.
 * @property icon Flavor icon, pre-decorated with a warning cross when flagged.
 * @property isPathDerivedName `true` when [name] is a path, so basename/middle-ellipsis shortening is meaningful. Only
 *   the row of a broken SDK has such a name. See PY-89560.
 */
@Serializable
class PyInterpreterItem(
  val ref: PyInterpreterRef,
  val name: @NlsSafe String,
  val suffix: @NlsSafe String?,
  val description: @NlsSafe String,
  val problem: PythonInterpreterProblem?,
  val icon: IconId,
  val isPathDerivedName: Boolean,
) {
  /** `name [suffix]` or `name` when [suffix] is null. Example: `~/Projects/myapp/long/path/.venv [3.12.1]`. */
  val fullName: @NlsSafe String = if (suffix == null) name else "$name [$suffix]"

  /**
   *  Up to 100 chars. For path-derived names: middle-ellipsizes the path. For free-form labels:
   *  trims the middle of the whole string. Keeps `[suffix]` verbatim.
   *  Example: `~/Projects/myapp/…/myenv [3.12.1]`.
   */
  val longName: @NlsSafe String = compactName(100, true)

  /**
   *  Up to 50 chars. For path-derived names: drops the path prefix. For free-form labels:
   *  trims the middle of the whole string. Keeps `[suffix]` verbatim.
   *  Example: `myenv [3.12.1]`.
   */
  val shortName: @NlsSafe String = compactName(50, false)

  /**
   * Fits the label in [maxLength] chars; `[suffix]` is kept as-is.
   *
   * @param keepPrefix only applies when [isPathDerivedName] is `true`.
   *   `true` → middle-ellipsize (`~/Projects…/myenv [3.12.1]`);
   *   `false` → drop prefix (`myenv [3.12.1]`).
   */
  fun compactName(maxLength: Int, keepPrefix: Boolean): @NlsSafe String {
    val suffix = if (suffix == null) "" else " [$suffix]"
    val availableForName = maxLength - suffix.length
    if (availableForName <= 0) {
      return fullName.trimMiddle(maxLength)
    }

    val shortened = if (isPathDerivedName) {
      shortenPath(name, availableForName, keepPrefix)
    }
    else {
      if (name.length <= availableForName) name else name.trimMiddle(availableForName)
    }
    return shortened + suffix
  }

  override fun equals(other: Any?): Boolean = ref == (other as? PyInterpreterItem)?.ref

  override fun hashCode(): Int = ref.hashCode()

  override fun toString(): String = "$ref: $name [$suffix] ($description)"
}

/**
 * Fits [path] in [maxLength] chars.
 *
 * Pure string work, so it costs no file system access and runs anywhere.
 *
 * @param keepPrefix `true` keeps the head of the path and ellipsizes the middle; `false` keeps the last segment only.
 */
fun shortenPath(path: String, maxLength: Int, keepPrefix: Boolean): String {
  val normalized = path.trimEnd { it == '/' || it == '\\' }
  val lastSeparatorIndex = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
  if (keepPrefix) {
    if (path.length <= maxLength) return path
    if (lastSeparatorIndex > 0) {
      val lastSegment = normalized.substring(lastSeparatorIndex)
      val availableForPrefix = maxLength - lastSegment.length - 1
      if (availableForPrefix > 0) {
        return normalized.substring(0, availableForPrefix) + ELLIPSIS + lastSegment
      }
    }
  }
  if (lastSeparatorIndex < 0) return path.takeLast(maxLength)
  val lastSegment = normalized.substring(lastSeparatorIndex + 1)
  return if (lastSegment.length <= maxLength) lastSegment else ELLIPSIS + lastSegment.takeLast((maxLength - 1).coerceAtLeast(0))
}
