package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2RepositoryType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class V2RepositoryExtensionsTests {

    @Nested
    inner class IsEquivalentToTest {

        private val remoteMavenCentral = V2Repository(
            id = "maven_central",
            url = "https://repo1.maven.org/maven2/",
            type = V2RepositoryType.MAVEN,
            alternateUrls = listOf("https://repo.maven.apache.org/maven2/"),
            friendlyName = "Maven Central"
        )

        private val remoteGMaven = V2Repository(
            id = "gmaven",
            url = "https://maven.google.com/",
            type = V2RepositoryType.MAVEN,
            alternateUrls = emptyList(),
            friendlyName = "Google Maven"
        )

        // TODO replace tests once build-tools based APIs are available

        @Test
        internal fun `maven_central(remote) is equivalent to mavenCentral(unified)`() {
            //val repository = GradleMavenRepository.MavenCentral
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteMavenCentral.isEquivalentTo(unifiedRepository)).isTrue()
        }

        @Test
        internal fun `maven_central(remote) is equivalent to generic(unified)`() {
            //val repository = GradleMavenRepository.Generic("https://repo1.maven.org/maven2/")
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteMavenCentral.isEquivalentTo(unifiedRepository)).isTrue()
        }

        @Test
        internal fun `maven_central(remote) is equivalent to generic(unified) with alternate URL`() {
            //val repository = GradleMavenRepository.Generic("https://repo.maven.apache.org/maven2/")
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteMavenCentral.isEquivalentTo(unifiedRepository)).isTrue()
        }

        @Test
        internal fun `maven_central(remote) is equivalent to generic(unified) with mangled alternate URL`() {
            //val repository = GradleMavenRepository.Generic("https://repo.maven.apache.org/MAVEN2/")
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteMavenCentral.isEquivalentTo(unifiedRepository)).isTrue()
        }

        @Test
        internal fun `maven_central(remote) is equivalent to generic(unified) with broken slash alternate URL`() {
            //val repository = GradleMavenRepository.Generic("https://repo.maven.apache.org/maven2")
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteMavenCentral.isEquivalentTo(unifiedRepository)).isTrue()
        }

        @Test
        internal fun `gmaven(remote) is equivalent to google(unified)`() {
            //val repository = GradleMavenRepository.Google
            //val unifiedRepository = GradleUnifiedDependencyRepositoryConverter.convert(repository)
            //
            //assertThat(remoteGMaven.isEquivalentTo(unifiedRepository)).isTrue()
        }
    }
}
