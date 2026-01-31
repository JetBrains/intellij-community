// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiDelegateReference
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.AstAccessControl.execute
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractReferenceResolveWithLibTest : AbstractReferenceResolveTest() {
    protected lateinit var mockLibraryFacility: MockLibraryFacility
        private set

    protected val testDirectoryPath: String
        get() = KotlinTestUtils.getTestDataFileName(this::class.java, this.name)!!

    protected open val attachLibrarySources: Boolean
        get() = true

    /**
     * Additional classpath for the library compilation.
     */
    protected open val libCompilationClasspath: List<File>
        get() = emptyList()

    /**
     * Additional compilation flags for library compilation.
     */
    protected open val libCompilationOptions: List<String>
        get() = emptyList()

    override fun fileName(): String {
        return KotlinTestUtils.getTestDataFileName(this::class.java, this.name) + "/src/" + getTestName(true) + ".kt"
    }

    override fun setUp() {
        super.setUp()

        val libraryDir = testDataDirectory.resolve(testDirectoryPath).resolve("lib")
        mockLibraryFacility = MockLibraryFacility(
            libraryDir,
            attachLibrarySources,
            classpath = libCompilationClasspath,
            options = libCompilationOptions,
        ).apply { setUp(module) }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun wrapReference(reference: PsiReference?): PsiReference? {
        if (reference == null) {
            return null
        } else if (InTextDirectivesUtils.isDirectiveDefined(myFixture.file.text, ALLOW_AST_ACCESS_DIRECTIVE)) {
            return reference
        }

        return object : PsiDelegateReference(reference) {
            override fun resolve(): PsiElement? {
                return execute(false, testRootDisposable, myFixture) {
                    reference.resolve() ?: error("Reference can't be resolved")
                }
            }

            override fun toString(): String {
                return reference.toString()
            }
        }
    }
}

abstract class AbstractReferenceResolveWithCompiledLibTest : AbstractReferenceResolveWithLibTest() {
    override val attachLibrarySources: Boolean
        get() = false

    override fun getExpectedReferences(text: String, index: Int): List<String> {
        // Check references to compiled elements first, then fallback to the shared 'REF' ones
        val decompiledReferences = getExpectedReferences(text, index, "CLS_REF")
        if (decompiledReferences.isNotEmpty()) {
            return decompiledReferences
        }

        return super.getExpectedReferences(text, index)
    }
}