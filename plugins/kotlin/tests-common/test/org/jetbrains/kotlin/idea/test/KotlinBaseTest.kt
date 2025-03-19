// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

class KotlinBaseTest {
    open class TestFile @JvmOverloads constructor(
        @JvmField val name: String,
        @JvmField val content: String,
        @JvmField val directives: Directives = Directives()
    ) : Comparable<TestFile> {
        override operator fun compareTo(other: TestFile): Int {
            return name.compareTo(other.name)
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is TestFile && other.name == name
        }

        override fun toString(): String {
            return name
        }
    }

    open class TestModule(
        @JvmField val name: String,
        @JvmField val dependenciesSymbols: List<String>,
        @JvmField val friendsSymbols: List<String>
    ) : Comparable<TestModule> {
        val dependencies: MutableList<TestModule> = arrayListOf()
        val friends: MutableList<TestModule> = arrayListOf()

        override fun compareTo(other: TestModule): Int = name.compareTo(other.name)

        override fun toString(): String = name
    }
}
