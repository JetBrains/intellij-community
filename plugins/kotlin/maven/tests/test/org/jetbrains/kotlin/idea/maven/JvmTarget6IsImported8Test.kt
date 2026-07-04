// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JvmTarget6IsImported8Test(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJvmTarget6IsImported8() = runBlocking {
        // Some version won't be imported into JPS (because it's some milestone version which wasn't published to MC) => explicit
        // JPS version during import will be dropped => we will fall back to the bundled JPS =>
        // we have to load 1.6 jvmTarget as 1.8 KTIJ-21515
        val (facet, notifications) = doJvmTarget6Test("1.7.0-RC")

        Assert.assertEquals("JVM 1.8", facet.targetPlatform!!.oldFashionedDescription)
        Assert.assertEquals("1.8", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)

        Assert.assertEquals(
            """
                    Title: 'Unsupported JVM target 1.6'
                    Content: 'Maven project uses JVM target 1.6 for Kotlin compilation, which is no longer supported. It has been imported as JVM target 1.8. Consider migrating the project to JVM 1.8.'
                """.trimIndent(),
            notifications.asText(),
        )
    }
}
