package com.jetbrains.python.sdk

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.trimMiddle
import com.jetbrains.python.sdk.impl.shortenPath
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon


/**
 * Pre-computed SDK label + icon for renderers (status bar, SDK popup, interpreter dropdown).
 * Built via `Sdk.pyInterpreterPresentation()`.
 *
 * @property name Main label: `sdk.name` or a caller-supplied override.
 * @property suffix Trailing info shown in brackets (e.g. `sudo / 3.12.1`), or `null`.
 * @property description SDK home path (or `[invalid]` if missing). Used in tooltips.
 * @property modifier `"invalid"` / `"unsupported"` if the SDK is flagged, otherwise `null`.
 * @property icon Flavor icon, pre-decorated with a warning cross when flagged.
 * @property isPathDerivedName `true` when [name] is the canonical path-derived label produced
 *   by `PythonSdkType.suggestSdkName` (so basename/middle-ellipsis shortening is meaningful);
 *   `false` when [name] is a free-form label such as `SSH (sftp://...)` or a caller-supplied
 *   custom name, in which case the renderer trims the middle instead. See PY-89560.
 */
@ApiStatus.Internal
data class PythonInterpreterPresentation(
  val name: @NlsSafe String,
  val suffix: @NlsSafe String?,
  val description: @NlsSafe String,
  val modifier: @NlsSafe String?,
  val icon: Icon,
  val isPathDerivedName: Boolean = true,
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
}

