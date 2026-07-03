package org.toml.lang.psi.ext

import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlValue

fun TomlKeyValueOwner.getValueByKey(key: String): TomlValue? =
    entries.find { it.key.text == key }?.value
