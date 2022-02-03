// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google

class GoogleAppCredentialsException
  : RuntimeException("Failed to get google app credentials from https://www.jetbrains.com/config/markdown.json")
