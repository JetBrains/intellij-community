// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.JpsBuildTestBase
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.junit.jupiter.api.BeforeEach

/**
 * Builds a Kotlin module with the bundled builder through the Build Tools API.
 *
 * The implementation is not on the test classpath. The test hands the directory of [KotlinBtaImplementation.BOOTSTRAP]
 * over to the builder, as the IDE hands it over to the build process.
 */
abstract class AbstractKotlinJpsBuilderTest : JpsBuildTestBase() {
  @BeforeEach
  fun setUpKotlinJpsBuilderTest() {
    KotlinBtaImplementation.BOOTSTRAP.install()
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
    CompilerSystemProperties.COMPILE_DAEMON_ENABLED_PROPERTY.clear()
  }

  /**
   * A module on the JDK of the test JVM, registered the way the IDE registers a modular JDK: with `jrt://` roots.
   * The builder hands such a JDK to the compiler as `-jdk-home`.
   */
  protected fun addModuleOnModularJdk(name: String, vararg srcPaths: String): JpsModule {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))
    val jdk = myModel.global.addSdk("modular-jdk", javaHome, System.getProperty("java.version"), JpsJavaSdkType.INSTANCE)
    jdk.addRoot(URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + javaHome + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
    return addModule(name, srcPaths, getAbsolutePath(getModuleOutputRelativePath(name)), null, jdk.properties)
  }

  protected fun addKotlinStdlib(module: JpsModule) {
    val library = myProject.addLibrary("kotlin-stdlib", JpsJavaLibraryType.INSTANCE)
    for (root in KotlinBtaImplementation.BOOTSTRAP.stdlibRoots) {
      library.addRoot(root.toPath(), JpsOrderRootType.COMPILED)
    }
    module.dependenciesList.addLibraryDependency(library)
  }
}
