// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.community.execService.EnvVarVal
import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus
import java.util.TreeMap

/**
 * Merges [EnvVar] to a map to be used for remote process.
 * [osFamily] dictates case sensitivity and a separator.
 * [EnvVarVal.NioPath] is converted to a right format and merged, [EnvVarVal.BareStr] replaces the previous value.
 * If a variable has the same path more than one time, only the first one stays.
 * [osFamily] dictates if this comparison is case-sensitive.
 */
@PyInternalExecApi
@ApiStatus.Internal
fun List<EnvVar>.toMap(osFamily: EelOsFamily): Map<String, String> {
  val caseSense = osFamily.isPosix
  val map = TreeMap<String, MutableList<String>>(if (caseSense) null else String.CASE_INSENSITIVE_ORDER)
  val sep = osFamily.pathSeparator
  val sepChar = sep[0]
  for ((key, value) in this) {
    val (newValue, merge) = when (value) {
      is EnvVarVal.BareStr -> value.str to false
      is EnvVarVal.NioPath -> {
        val path = value.path
        assert(path.getEelDescriptor().osFamily == osFamily) { "Path from a wrong OS family?" }
        path.asEelPath().toString() to true
      }
      is EnvVarVal.RemotePath -> value.path to true
    }
    val currentValueInMap = map.getOrPut(key) { mutableListOf() }
    if (merge) {
      currentValueInMap += newValue.trim(sepChar)
    }
    else {
      currentValueInMap.clear()
      currentValueInMap += newValue
    }
  }
  return map.mapValuesTo(TreeMap(map.comparator())) {
    // An empty part of a path list is the current directory on POSIX: never add it.
    it.value.filter { s -> s.isNotEmpty() }.distinctBy { s ->
      if (caseSense) {
        s
      }
      else {
        s.lowercase()
      }
    }.joinToString(sep)
  }
}
