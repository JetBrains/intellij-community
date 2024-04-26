// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

enum class RequirementType(val kind: String) {
  NAME("package"),
  URL("package"),
  REFERENCE("file"),
  EDITABLE("url"),
  PATH("path")
}
