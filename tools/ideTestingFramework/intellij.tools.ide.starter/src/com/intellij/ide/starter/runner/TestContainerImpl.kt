package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import org.kodein.di.direct
import org.kodein.di.instance

class TestContainerImpl(
  override val ciServer: CIServer = di.direct.instance(),
  override var useLatestDownloadedIdeBuild: Boolean = false,
  override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf()
) : TestContainer<TestContainerImpl> {
  override lateinit var testContext: IDETestContext
}