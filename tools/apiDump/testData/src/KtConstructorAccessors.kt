@file:Suppress("unused", "UNUSED_PARAMETER")

package com.intellij.tools.apiDump.testData

/**
 * @see com.intellij.tools.apiDump.isConstructorAccessor
 */
class KtConstructorAccessors private constructor() {

  class Accessor private constructor() {
    private companion object {
      fun access() {
        Accessor()
      }
    }
  }

  class AccessorWithParam private constructor(s: String) {
    private companion object {
      fun access() {
        AccessorWithParam("")
      }
    }
  }

  class AccessorWithOnlyInt private constructor(i: Int) {
    private companion object {
      fun access() {
        AccessorWithOnlyInt(42)
      }
    }
  }

  // Same signature as DefaultConstructorParameters => has to be included in dump.
  class AccessorWithLastInt private constructor(s: String, i: Int) {
    private companion object {
      fun access() {
        AccessorWithLastInt("", 42)
      }
    }
  }

  class DefaultConstructorParameters(s: String = "")
}
