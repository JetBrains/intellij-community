// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions

import org.intellij.lang.regexp.psi.RegExpPattern
import java.util.regex.Pattern

fun RegExpPattern.asPattern() = Pattern.compile(text)!!