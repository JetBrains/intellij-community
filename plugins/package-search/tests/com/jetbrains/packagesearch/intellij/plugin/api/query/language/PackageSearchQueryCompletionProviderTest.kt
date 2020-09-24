package com.jetbrains.packagesearch.intellij.plugin.api.query.language

import assertk.assertThat
import assertk.assertions.isNull
import com.jetbrains.packagesearch.intellij.plugin.api.query.SampleQueryCompletionTests
import org.junit.jupiter.api.Test

/** @see SampleQueryCompletionTests for more examples */
class PackageSearchQueryCompletionProviderTest {

    private val completionProvider = PackageSearchQueryCompletionProvider()

    @Test
    fun `should not provide completion when caret is not in completable query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor", 4)

        assertThat(completion).isNull()
    }
}
