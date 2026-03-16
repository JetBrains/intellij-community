package com.intellij.ide.starter.runner.targets

import com.intellij.ide.starter.di.di
import com.intellij.platform.eel.EelApi
import org.kodein.di.direct
import org.kodein.di.instance

/** Returns which target is currently executed */
interface TargetResolver {
  val current: TargetIdentifier

  companion object {
    val instance: TargetResolver
      get() = di.direct.instance<TargetResolver>()
  }
}

object LocalOnlyTargetResolver : TargetResolver {
  override val current: TargetIdentifier = object : TargetIdentifier.Local {
    override val eelApi: EelApi
      get() = TODO("Should not be called in publicly available Starter till EelFixtures from IJent test framework will be part of community repo")
  }
}