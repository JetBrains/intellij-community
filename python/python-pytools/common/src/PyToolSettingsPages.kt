// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.common

/**
 * The id of the External Tools settings page.
 *
 * The frontend declares the page, so a backend module knows the id and not the class. The id is here because both
 * sides use it.
 */
const val PY_EXTERNAL_TOOLS_SETTINGS_ID: String = "python.external.tools.group.settings"
