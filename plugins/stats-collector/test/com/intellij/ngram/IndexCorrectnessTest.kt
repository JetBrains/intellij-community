package com.intellij.ngram

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.stats.ngram.NGramFileBasedIndex
import com.intellij.util.indexing.FileBasedIndex
import junit.framework.TestCase

class IndexCorrectnessTest : LightFixtureCompletionTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.configureByText(JavaFileType.INSTANCE, """
            public class Foo {
               void foo() {
               }
               void bar() {
                  foo();
                  foo();
                  foo();
                  foo();
                  foo();
               }
            }

            """)
    }

    fun testIndexContent() {
        val allKeys = FileBasedIndex.getInstance().getAllKeys(NGramFileBasedIndex.KEY, project!!)
        var counter = 0
        allKeys.forEach {
            val numberOfOccurrences = NGramFileBasedIndex.getNumberOfOccurrences(it, GlobalSearchScope.allScope(project))
            if (numberOfOccurrences > 0 && it.elements.last() == "foo" && it.elements.size == 6) {
                counter += numberOfOccurrences
            }
        }
        TestCase.assertEquals(counter, 5)
    }
}