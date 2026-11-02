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
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Builds a Kotlin module with the bundled builder through the Build Tools API.
 *
 * The implementation is not on the test classpath. The test hands the directory of [KotlinBtaImplementation.CURRENT]
 * over to the builder, as the IDE hands it over to the build process.
 */
class KotlinJpsBuilderTest : JpsBuildTestBase() {
  @BeforeEach
  fun setUpKotlinJpsBuilderTest() {
    KotlinBtaImplementation.CURRENT.install()
    System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
    System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
  }

  @Test
  fun testCompilesOnlyTheChangedFile() {
    createFile("src/a.kt", "package p\n\nfun a(): Int = 1\n")
    createFile("src/b.kt", "package p\n\nfun b(): Int = a() + 1\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    rebuildAllModules()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/a.kt", "src/b.kt")
    val output = getModuleOutput(module)
    assertTrue(File(output, "p/AKt.class").isFile)
    assertTrue(File(output, "p/BKt.class").isFile)
    assertTrue(File(output, "META-INF/m.kotlin_module").isFile)

    makeAll().assertUpToDate()

    change(getAbsolutePath("src/b.kt"), "package p\n\nfun b(): Int = a() + 2\n")
    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/b.kt")
    assertTrue(File(output, "p/AKt.class").isFile)
    assertTrue(File(output, "META-INF/m.kotlin_module").isFile)

    makeAll().assertUpToDate()
  }

  @Test
  fun testRecompilesTheUserOfAChangedDeclaration() {
    createFile("src/a.kt", "package p\n\nfun a(): Int = 1\n")
    createFile("src/b.kt", "package p\n\nfun b() = a()\n")
    createFile("src/c.kt", "package p\n\nfun c(): String = \"c\"\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    rebuildAllModules()

    change(getAbsolutePath("src/a.kt"), "package p\n\nfun a(): String = \"a\"\n")
    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/a.kt", "src/b.kt")
  }

  /**
   * A module on the JDK of the test JVM, registered the way the IDE registers a modular JDK: with `jrt://` roots.
   * The builder hands such a JDK to the compiler as `-jdk-home`.
   */
  private fun addModuleOnModularJdk(name: String, vararg srcPaths: String): JpsModule {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))
    val jdk = myModel.global.addSdk("modular-jdk", javaHome, System.getProperty("java.version"), JpsJavaSdkType.INSTANCE)
    jdk.addRoot(URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + javaHome + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
    return addModule(name, srcPaths, getAbsolutePath(getModuleOutputRelativePath(name)), null, jdk.properties)
  }

  private fun addKotlinStdlib(module: JpsModule) {
    val library = myProject.addLibrary("kotlin-stdlib", JpsJavaLibraryType.INSTANCE)
    for (root in KotlinBtaImplementation.CURRENT.stdlibRoots) {
      library.addRoot(root.toPath(), JpsOrderRootType.COMPILED)
    }
    module.dependenciesList.addLibraryDependency(library)
  }
}
