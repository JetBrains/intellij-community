package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Scm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource

internal class ScmUrlExtractorTest {

    private fun getAScm(url: String = "https://github.com/potato/walrus") = StandardV2Scm(url)

    @Test
    internal fun `should return null when passed null SCM`() {
        assertThat(extractScmUrl(null)).isNull()
    }

    @ParameterizedTest(name = "[{index}] should return (GITHUB, {1}) when passed non-null SCM with a GitHub URL: {0}")
    @ArgumentsSource(GitHubScmUrlTestArgumentsProvider::class)
    internal fun `should return (GITHUB, scm_url) when passed non-null SCM with a GitHub URL`(url: String, expectedUrl: String) {
        assertThat(extractScmUrl(getAScm(url))).isNotNull().all {
            prop(ScmUrl::type).isEqualTo(ScmUrl.Type.GITHUB)
            prop(ScmUrl::url).isEqualTo(expectedUrl)
        }
    }

    class GitHubScmUrlTestArgumentsProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?) = createArguments(
            "https://github.com/potato/walrus" to "https://github.com/potato/walrus",
            "ssh://git@github.com/potato/walrus.git" to "https://github.com/potato/walrus.git",
            "https://github.com/khatilov/CustomMatcher" to "https://github.com/khatilov/CustomMatcher",
            "https://github.com/aws/aws-iot-device-sdk-java.git" to "https://github.com/aws/aws-iot-device-sdk-java.git",
            "git@github.com:JetBrains/kotlin.git" to "https://github.com/JetBrains/kotlin.git"
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://svn.activemq.codehaus.org/trunk/activemq/",
            "http://sourceforge.net/p/abbot/svn/HEAD/tree/abbot/trunk/",
            "https://example.com/test",
            "https://example.com/test.git",
            "ssh://git@example.com/test.git",
            "git@example.com/test.git",
            "http://cvs.sourceforge.net/cgi-bin/viewcvs.cgi/acegisecurity/acegisecurity/samples/annotations/"
        ]
    )
    internal fun `should return (GENERIC, scm_url) when passed non-null SCM with a generic URL`(url: String) {
        assertThat(extractScmUrl(getAScm(url))).isNotNull().all {
            prop(ScmUrl::type).isEqualTo(ScmUrl.Type.GENERIC)
            prop(ScmUrl::url).isEqualTo(url)
        }
    }
}

private fun <A, B> createArguments(vararg values: Pair<A, B>) = values.map { Arguments.of(it.first, it.second) }
    .stream()
