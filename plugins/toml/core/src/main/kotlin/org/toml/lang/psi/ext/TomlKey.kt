/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.ext

import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment

/**
 * If key consists of single [TomlKeySegment], returns its name.
 * Otherwise, returns `null`
 */
val TomlKey.name: String? get() = segments.singleOrNull()?.name
