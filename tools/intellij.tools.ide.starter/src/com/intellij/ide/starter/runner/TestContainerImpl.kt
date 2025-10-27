package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext

class TestContainerImpl private constructor() : TestContainer<TestContainerImpl> {
  override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf()
}