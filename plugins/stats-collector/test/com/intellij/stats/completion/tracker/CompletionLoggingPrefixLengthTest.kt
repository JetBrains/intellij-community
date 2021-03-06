// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.completion.ml.util.prefix
import org.assertj.core.api.Assertions.assertThat

class CompletionLoggingPrefixLengthTest: CompletionLoggingTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addClass("interface Rum {}")
    myFixture.addClass("interface Runn {}")
  }

  fun `test completion with prefix 0 after dot`() {
    myFixture.type('.')
    myFixture.completeBasic()

    val prefixLength = lookup.prefix().length

    myFixture.type("ru\n")
    assertThat(prefixLength).isEqualTo(0)
  }

  fun `test completion with prefix 2 after dot`() {
    myFixture.type(".ru")
    myFixture.completeBasic()

    val prefixLength = lookup.prefix().length

    assertThat(prefixLength).isEqualTo(2)
  }


  fun `test completion with prefix 3`() {
    myFixture.type('\b')
    myFixture.type("Run")
    myFixture.completeBasic()

    val prefixLength = lookup.prefix().length

    myFixture.type('\n')

    assertThat(prefixLength).isEqualTo(3)
  }


  fun `test completion with prefix 1`() {
    myFixture.type('\b')
    myFixture.type('R')
    myFixture.completeBasic()

    val prefixLength = lookup.prefix().length

    myFixture.type("un\n")

    assertThat(prefixLength).isEqualTo(1)
  }
}