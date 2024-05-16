@file:Suppress("unused")

package com.intellij.tools.apiDump.testData

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun apiInternalFunction() {
}

@Internal
val apiInternalProperty: Unit = Unit
