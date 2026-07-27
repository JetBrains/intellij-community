// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.jetbrains.python.PythonLanguage

/**
 * Master switch (flag 1) for the PY-85946 new Python formatter defaults. When off, the whole feature
 * is inert: classic everywhere, no migration, no balloon — byte-identical to the historical behavior.
 */
const val PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY: String = "python.formatter.new.defaults.enabled"

/**
 * Per-installation rollout state (flag 2): whether this installation actually uses the new defaults.
 * Managed by `PyCodeStyleMigrationActivity` — on for fresh installs, off for upgrades until the user
 * accepts the switch balloon. Only has an effect while the master switch ([PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY]) is on.
 */
const val PY_NEW_FORMATTER_DEFAULTS_ACTIVE_KEY: String = "python.formatter.new.defaults"

/** The master switch (flag 1): whether the new-defaults feature is enabled at all. */
fun isPyNewFormatterDefaultsFeatureEnabled(): Boolean = Registry.`is`(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY, false)

/**
 * Whether the modern defaults are the active baseline for this installation: the master switch (flag 1)
 * and the per-installation rollout state (flag 2) are both on.
 */
fun isPyNewFormatterDefaultsActive(): Boolean =
  isPyNewFormatterDefaultsFeatureEnabled() && Registry.`is`(PY_NEW_FORMATTER_DEFAULTS_ACTIVE_KEY, false)

/**
 * The profile a fresh Python scheme defaults to: the modern [PyDefaultStyleGuide] when the new defaults
 * are active (so the IDE-level "Default" scheme simply *is* modern and reads clean), otherwise the
 * historical [PyClassicStyleGuide].
 */
fun defaultPyCodeStyleId(): String =
  if (isPyNewFormatterDefaultsActive()) PyDefaultStyleGuide.CODE_STYLE_ID else PyClassicStyleGuide.CODE_STYLE_ID

val CodeStyleSettings.pyCommonSettings: PyCommonCodeStyleSettings?
  get() = getCommonSettings(PythonLanguage.getInstance()) as? PyCommonCodeStyleSettings

val CodeStyleSettings.pyCustomSettings: PyCodeStyleSettings
  get() = getCustomSettings(PyCodeStyleSettings::class.java)

/**
 * The active Python code style profile id, but only when the custom and common settings agree on it
 * (otherwise the scheme is in an inconsistent/partially-migrated state and we report no profile).
 *
 * @see PyDefaultStyleGuide
 * @see PyClassicStyleGuide
 */
fun CodeStyleSettings.pyCodeStyleProfile(): String? = pyCustomSettings.CODE_STYLE_PROFILE?.takeIf { customStyleId ->
  customStyleId == pyCommonSettings?.CODE_STYLE_PROFILE
}
