// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.search

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.FileContentImpl
import java.lang.ref.Reference
import kotlin.time.Duration.Companion.minutes

class KotlinAnnotatedElementsSearcherTest : AbstractSearcherTest() {
    override fun runInDispatchThread(): Boolean = false

    fun testReindexesFileWhenAnnotationIndexPointsToClass(): Unit = timeoutRunBlocking(timeout = 1.minutes) {
        myFixture.addClass("package test; public @interface StubRecoveryAnnotation {}")
        val packageLine = "package test\n"
        val annotation = "@StubRecoveryAnnotation\n"
        val precedingClasses = "class First\nclass Second\n"
        val target = "class StubRecoveryTarget\n"
        val file = myFixture.addFileToProject(
            "test/StubRecoveryTarget.kt",
            packageLine + precedingClasses + annotation + target
        )
        IndexingTestUtil.suspendUntilIndexesAreReady(project)

        val virtualFile = file.virtualFile
        val scope = GlobalSearchScope.fileScope(project, virtualFile)
        val errors = mutableListOf<String>()
        val errorProcessor = object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
                if (!message.contains("psiElement is not instance of requiredClass")) {
                    return super.processError(category, message, details, t)
                }
                errors.add(message)
                return Action.NONE
            }
        }
        readActionBlocking {
            val index = FileBasedIndex.getInstance() as FileBasedIndexEx
            assertNotNull(index.getSingleEntryIndexData(StubUpdatingIndex.INDEX_ID, virtualFile, project))
            val ast = file.node
            try {
                // The old annotation stub ID points to a class in the current AST.
                val staleText = packageLine + annotation + precedingClasses + target
                val staleContent = FileContentImpl.createByContent(virtualFile, { staleText.toByteArray() }, project)
                val stubIndex = index.getIndex(StubUpdatingIndex.INDEX_ID)
                index.runCleanupAction {
                    stubIndex.mapInputAndPrepareUpdate(FileBasedIndex.getFileId(virtualFile), staleContent).update()
                }
                LoggedErrorProcessor.executeWith(errorProcessor).use {
                    assertEmpty(findAnnotatedMembers(scope))
                }
            } finally {
                Reference.reachabilityFence(ast)
            }
        }
        val error = assertOneElement(errors)
        assertTrue(error, error.contains("Looking for StubRecoveryAnnotation in"))

        IndexingTestUtil.suspendUntilIndexesAreReady(project)
        assertEquals(listOf("StubRecoveryTarget"), readAction { findAnnotatedMembers(scope) })
    }

    @RequiresReadLock
    private fun findAnnotatedMembers(scope: GlobalSearchScope): List<String?> {
        val annotationClass = getPsiClass("test.StubRecoveryAnnotation")
        return AnnotatedElementsSearch.searchElements(annotationClass, scope, PsiClass::class.java).findAll().map { it.name }
    }
}
