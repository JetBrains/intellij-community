@file:Suppress("unused")

package com.intellij.tools.apiDump.testData

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

class KtPublicClass private constructor() {

  class PublicClass private constructor()

  @Internal
  class ApiInternalClass private constructor()

  @Experimental
  class ApiExperimentalClass private constructor()
}

@Internal
open class KtApiInternalClass private constructor() {

  class PublicClass private constructor()

  @Internal
  class ApiInternalClass private constructor()

  @Experimental
  class ApiExperimentalClass private constructor()
}

@Experimental
open class KtApiExperimentalClass private constructor() {

  class PublicClass private constructor()

  @Internal
  class ApiInternalClass private constructor()

  @Experimental
  class ApiExperimentalClass private constructor()
}
