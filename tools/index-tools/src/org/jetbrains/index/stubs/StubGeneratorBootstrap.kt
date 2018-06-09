// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.stubs

import com.intellij.ide.BootstrapClassLoaderUtil

interface StubGeneratorBootstrap {
  fun run(args: Array<String>)
  fun <T> Array<out T>.secondOrNull() = if (size < 2) null else this[1]
}

fun bootstrapAndRun(args: Array<String>, className: String?) {
  val newClassLoader = BootstrapClassLoaderUtil.initClassLoader()
  Thread.currentThread().contextClassLoader = newClassLoader

  val klass = Class.forName(className, true, newClassLoader)
  val instance = klass.newInstance()
  val method = klass.getDeclaredMethod("run", Array<String>::class.java)
  method.invoke(instance, args)
}
