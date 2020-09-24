package com.jetbrains.packagesearch.intellij.plugin.api.query

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.SampleQueryCompletionProvider
import org.junit.jupiter.api.Test

class SampleQueryCompletionTests {

    private val completionProvider = SampleQueryCompletionProvider()

    @Test
    fun `does not provide completion when caret not in completable query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor", 4)

        assertThat(completion).isNull()
    }

    @Test
    fun `provides attribute completion when caret after space`() {
        val completion = completionProvider.buildCompletionModel("ktor ", 5)

        assertThat(completion).isNotNull().and {
            assertThat(caretPosition).isEqualTo(5)
            assertThat(endPosition).isEqualTo(5)
            assertThat(prefix).isNullOrEmpty()
            assertThat(attributes).isNotNull()
                .containsOnly("/onlyStable", "/tag", "/onlyMpp")
            assertThat(values).isNullOrEmpty()
        }
    }

    @Test
    fun `provides attribute completion when caret in partial query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor /on", 8)

        assertThat(completion).isNotNull().and {
            assertThat(caretPosition).isEqualTo(5)
            assertThat(endPosition).isEqualTo(8)
            assertThat(prefix).isEqualTo("/on")
            assertThat(attributes).isNotNull()
                .containsOnly("/onlyStable", "/onlyMpp")
            assertThat(values).isNullOrEmpty()
        }
    }

    @Test
    fun `provides attribute completion when caret in complete query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor /tag", 9)

        assertThat(completion).isNotNull().and {
            assertThat(caretPosition).isEqualTo(5)
            assertThat(endPosition).isEqualTo(9)
            assertThat(prefix).isEqualTo("/tag")
            assertThat(attributes).isNotNull()
                .containsOnly("/tag")
            assertThat(values).isNullOrEmpty()
        }
    }

    @Test
    fun `provides value completion when caret after query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor /onlyStable:", 17)

        assertThat(completion).isNotNull().and {
            assertThat(caretPosition).isEqualTo(17)
            assertThat(endPosition).isEqualTo(17)
            assertThat(prefix).isNullOrEmpty()
            assertThat(attributes).isNullOrEmpty()
            assertThat(values).isNotNull()
                .containsOnly("true", "false")
        }
    }

    @Test
    fun `provides value completion when caret in partial attribute value`() {
        val completion = completionProvider.buildCompletionModel("ktor /onlyStable:tr", 19)

        assertThat(completion).isNotNull().and {
            assertThat(caretPosition).isEqualTo(17)
            assertThat(endPosition).isEqualTo(19)
            assertThat(prefix).isEqualTo("tr")
            assertThat(attributes).isNullOrEmpty()
            assertThat(values).isNotNull()
                .containsOnly("true")
        }
    }

    private fun <T> Assert<T>.and(assertions: T.() -> Unit) = given { assertions(it) }

    @Test
    fun `does not provide completion when caret in non-completable query attribute`() {
        val completion = completionProvider.buildCompletionModel("ktor /tag:", 10)

        assertThat(completion).isNull()
    }

    @Test
    fun `does not provide completion when caret in non-completable attribute value`() {
        val completion = completionProvider.buildCompletionModel("ktor /tag:test", 10)

        assertThat(completion).isNull()
    }
}
