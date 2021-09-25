// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.eval4j.test

fun getTestName(methodName: String) = if (methodName.startsWith("test")) methodName else "test${methodName.capitalize()}"