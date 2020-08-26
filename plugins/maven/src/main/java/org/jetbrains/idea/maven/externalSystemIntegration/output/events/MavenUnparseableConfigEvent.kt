// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputActionProcessor

class MavenUnparseableConfigEvent(parentId: Any?,
                                  eventTime: Long,
                                  @BuildEventsNls.Message message: String) : AbstractBuildEvent(Object(), parentId, eventTime, message), MavenBuildEvent {

  override fun process(processor: MavenOutputActionProcessor) {
    processor.showMavenInvalidConfig(message)
  }
}
