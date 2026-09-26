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
 * @property ref what to hand back to select this interpreter. A list of registered interpreters builds
 *   [PyInterpreterRef.ExistingSdk].
 * @property name Main label: `sdk.name` or a caller-supplied override. Two items built for one interpreter with
 *   different names are still equal, so never put both in one list.
 * @property suffix Trailing info shown in brackets (e.g. `sudo / 3.12.1`), or `null`.
 * @property description Python Binary Path (SDK.homePath). Used in tooltips.
 * @property problem Why the interpreter is flagged, or `null` when it is usable.
 * @property icon Flavor icon, pre-decorated with a warning cross when flagged.
 * @property isPathDerivedName `true` when [name] is the canonical path-derived label produced
 *   by `PythonSdkType.suggestSdkName` (so basename/middle-ellipsis shortening is meaningful);
 *   `false` when [name] is a free-form label such as `SSH (sftp://...)` or a caller-supplied
 *   custom name, in which case the renderer trims the middle instead. See PY-89560.
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
  /**
   * A short label the interpreter's own tool supplies, or `null` to shorten [name] as usual.
   *
   * It replaces [shortName] alone. [name], [fullName] and [longName] stay the SDK name, because they are read where
   * there is room for the path, and the path is what tells two environments apart.
   */
  val toolShortName: @NlsSafe String? = null,
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
   *
   *  A [toolShortName] is taken as it stands, with no `[suffix]` after it. The tool wrote the whole label, so appending
   *  the version to it would state the version twice.
   */
  val shortName: @NlsSafe String = toolShortName ?: compactName(50, false)

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
