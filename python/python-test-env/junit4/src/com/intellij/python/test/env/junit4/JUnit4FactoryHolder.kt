package com.intellij.python.test.env.junit4

import com.intellij.python.test.env.common.createPyEnvironmentFactory
import com.intellij.python.test.env.core.PyEnvironmentFactory

object JUnit4FactoryHolder {

  private val factory: PyEnvironmentFactory by lazy {
    val f = createPyEnvironmentFactory()
    Runtime.getRuntime().addShutdownHook(Thread {
      f.close()
    })
    f
  }

  fun getOrCreate(): PyEnvironmentFactory {
    return factory
  }

}
