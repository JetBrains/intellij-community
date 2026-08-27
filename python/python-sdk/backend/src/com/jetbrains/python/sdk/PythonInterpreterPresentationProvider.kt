// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus

/**
 * Lets the tool that owns an interpreter write its short label, where the default one reads badly.
 *
 * The default short label is the last folder of the interpreter path. For most tools that folder is the environment, and
 * reads well. A tool that keeps its environments in a cache directory names each one with a generated string, which
 * reads as noise, so such a tool labels its interpreters here instead.
 *
 * Only the short label changes. The SDK name stays what it was, and every place with room for the path still shows it,
 * because the path is what tells two environments apart.
 *
 * The tool decides for each interpreter. It returns `null` from [getShortName] for the ones the default already serves.
 * A caller that passes a name of its own is never overridden either: it named that one rendering, and this names the
 * interpreter.
 *
 * One provider serves one flavor, and an interpreter has one flavor, so no call site chooses between providers. This is
 * the same selection that `PyProjectManager.forSdk` makes.
 */
@ApiStatus.Internal
interface PythonInterpreterPresentationProvider {
  /** The flavor whose interpreters this provider labels. */
  val flavorType: Class<out PythonSdkFlavor<*>>

  /**
   * The short label to show for [sdk], or `null` to keep the shortened path.
   *
   * It is shown as it stands, with nothing appended and nothing cut, so it must be short enough for a status bar.
   *
   * Called while the IDE paints, so it must not run a process or read a file.
   */
  fun getShortName(sdk: Sdk): @NlsSafe String?

  companion object {
    internal val EP: ExtensionPointName<PythonInterpreterPresentationProvider> =
      ExtensionPointName.create("Pythonid.interpreterPresentationProvider")

    /** The short label the tool of [sdk] writes. It is `null` when the tool has no provider, or it keeps the default. */
    internal fun shortNameFor(sdk: Sdk): @NlsSafe String? {
      val flavor = sdk.pySdkAdditionalData.flavor
      return EP.extensionList.firstOrNull { it.flavorType.isInstance(flavor) }?.getShortName(sdk)
    }
  }
}
