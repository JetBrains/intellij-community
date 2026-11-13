// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.JpsBuildTestBase
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.junit.jupiter.api.BeforeEach

/**
 * The base of the tests that build a project through the Build Tools API without the test data of the Kotlin
 * repository.
 *
 * The implementation is not on the test classpath. The base hands its directory over to the builder, as the IDE hands
 * it over to the build process. A test switches the implementation by assigning [btaImplementation] and calling
 * [KotlinBtaImplementation.install] before it creates the project.
 */
abstract class KotlinBtaJpsBuildTestBase : JpsBuildTestBase() {
  /** The implementation that the tests compile with. A test switches it before it creates the project. */
  protected var btaImplementation: KotlinBtaImplementation = KotlinBtaImplementation.BOOTSTRAP

  @BeforeEach
  fun setUpKotlinBtaJpsBuildTestBase() {
    btaImplementation.install()
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
  }

  /**
   * A module on the JDK of the test JVM, registered the way the IDE registers a modular JDK: with `jrt://` roots.
   * The builder hands such a JDK to the compiler as `-jdk-home`.
   */
  private val modularJdk: JpsSdk<JpsDummyElement> by lazy {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))
    val jdk = myModel.global.addSdk("modular-jdk", javaHome, System.getProperty("java.version"), JpsJavaSdkType.INSTANCE)
    jdk.addRoot(URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + javaHome + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
    jdk.properties
  }

  protected fun addModuleOnModularJdk(name: String, vararg srcPaths: String): JpsModule =
    addModule(name, srcPaths, getAbsolutePath(getModuleOutputRelativePath(name)), null, modularJdk)

  /** Adds a `kotlin-stdlib` library of [btaImplementation] to [modules]. */
  protected fun addKotlinStdlib(vararg modules: JpsModule) {
    val library = myProject.addLibrary("kotlin-stdlib", JpsJavaLibraryType.INSTANCE)
    for (root in btaImplementation.stdlibRoots) {
      library.addRoot(root.toPath(), JpsOrderRootType.COMPILED)
    }
    for (module in modules) {
      module.dependenciesList.addLibraryDependency(library)
    }
  }
}
