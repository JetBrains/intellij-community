@file:Suppress("unused")

package com.intellij.tools.apiDump.testData

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

private class PrivateClassWithCompanionObject {
  companion object
}

@Internal
class ApiInternalClassWithCompanion private constructor() {
  companion object
}

@Experimental
class ApiExperimentalClassWithCompanion private constructor() {
  companion object
}

class ClassWithApiInternalCompanion private constructor() {
  @Internal
  companion object
}

class ClassWithApiExperimentalCompanion private constructor() {
  @Experimental
  companion object
}
