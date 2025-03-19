// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.jetbrains.python.packaging.PyPackageVersionNormalizer

interface Logical {
  fun check(values: Map<String, String?>): Boolean
}

class True : Logical {
  override fun check(values: Map<String, String?>): Boolean {
    return true
  }
}

class False : Logical {
  override fun check(values: Map<String, String?>): Boolean {
    return false
  }
}

class Or(private vararg val items: Logical) : Logical {
  override fun check(values: Map<String, String?>): Boolean {
    if (items.isEmpty()) {
      return true
    }
    return items.any { it.check(values) }
  }
}

class And(private vararg val items: Logical) : Logical {
  override fun check(values: Map<String, String?>): Boolean {
    return items.all { it.check(values) }
  }
}

class Expression(private val variable: String, private val operation: String, private val value: String) : Logical {
  private val isVersion: Boolean
    get() {
      return variable in VERSION_VARIABLES
    }

  private fun checkVersion(actual: String, value: String): Boolean {
    return compareVersions(PyPackageVersionNormalizer.normalize(actual), operation, PyPackageVersionNormalizer.normalize(value))
  }

  private fun checkOther(actual: String, value: String): Boolean {
    if (operation == "===") {
      return actual == value
    }

    return when (operation) {
      "==" -> {
        actual == value
      }
      "!=" -> {
        actual != value
      }
      else -> false
    }
  }

  override fun check(values: Map<String, String?>): Boolean {
    val actual = values[variable] ?: return false

    return if (isVersion) {
      checkVersion(actual, value)
    }
    else {
      checkOther(actual, value)
    }
  }
}
