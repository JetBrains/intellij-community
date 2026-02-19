@file:Suppress("unused")

package com.intellij.tools.apiDump.testData

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

class KtFieldAnnotations private constructor() {

  @Internal
  val apiInternalProperty: Any? = null

  @get:Internal
  val apiInternalPropertyGetter: Any? = null

  @set:Internal
  var apiInternalPropertySetter: Any? = null

  @get:Internal
  @set:Internal
  var apiInternalPropertyGetterSetter: Any? = null

  @Internal
  @JvmField
  val apiInternalField: Any? = null

  @Experimental
  val apiExperimentalProperty: Any? = null

  @get:Experimental
  val apiExperimentalPropertyGetter: Any? = null

  @set:Experimental
  var apiExperimentalPropertySetter: Any? = null

  @get:Experimental
  @set:Experimental
  var apiExperimentalPropertyGetterSetter: Any? = null

  @Experimental
  @JvmField
  val apiExperimentalField: Any? = null
}
