// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Builds a Kotlin module in process. */
class KotlinJpsBuilderTest : AbstractKotlinJpsBuilderTest() {
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
   * The Kotlin file leaves the imported static member unused, so the compiled output keeps no trace of the import.
   * Only the import usage tells JPS to recompile the file when the member loses its static modifier. The build then
   * reports the import that no longer resolves, so it fails.
   */
  @Test
  fun testRecompilesTheFileThatImportsAMemberThatBecameNonStatic() {
    createFile("src/p/Util.java", "package p;\n\npublic class Util { public static int method() { return 1; } }\n")
    createFile("src/k.kt", "package p\n\nimport p.Util.method\n\nfun k(): Int = 1\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    rebuildAllModules()
    buildAllModules().assertUpToDate()

    change(getAbsolutePath("src/p/Util.java"), "package p;\n\npublic class Util { public int method() { return 1; } }\n")
    buildAllModules()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/k.kt")
  }

  /**
   * A star import can bring in a static member that appears later, so the graph also takes it as a static star import.
   * A new static member of the imported scope then recompiles the file.
   */
  @Test
  fun testRecompilesTheFileOfAStarImportWhenAStaticMemberAppears() {
    createFile("src/p/Util.java", "package p;\n\npublic class Util { }\n")
    createFile("src/k.kt", "package p\n\nimport p.Util.*\n\nfun k(): Int = 1\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    rebuildAllModules()
    buildAllModules().assertUpToDate()

    change(getAbsolutePath("src/p/Util.java"), "package p;\n\npublic class Util { public static int method() { return 1; } }\n")
    buildAllModules().assertSuccessful()
    assertCompiled(KotlinBuilder.KOTLIN_BUILDER_NAME, "src/k.kt")
  }
}
