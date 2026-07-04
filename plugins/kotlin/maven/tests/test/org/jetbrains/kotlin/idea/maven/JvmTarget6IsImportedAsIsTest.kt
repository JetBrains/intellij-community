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
class JvmTarget6IsImportedAsIsTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJvmTargetIsImportedAsIs() = runBlocking {
        // If version isn't specified then we will fall back to bundled frontend which is already downloaded => Unbundled JPS can be used
        val (facet, notifications) = doJvmTarget6Test(version = null)
        Assert.assertEquals("JVM 1.6", facet.targetPlatform!!.oldFashionedDescription)
        Assert.assertEquals("1.6", (facet.compilerArguments as K2JVMCompilerArguments).jvmTarget)
        Assert.assertEquals("", notifications.asText())
    }
}
