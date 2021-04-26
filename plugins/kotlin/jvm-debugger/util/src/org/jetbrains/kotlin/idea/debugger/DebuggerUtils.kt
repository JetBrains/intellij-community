// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.idea.core.KotlinFileTypeFactoryUtils
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil.findFilesWithExactPackage
import org.jetbrains.kotlin.idea.stubindex.StaticFacadeIndexUtil
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

object DebuggerUtils {
    @get:TestOnly
    var forceRanking = false

    private val IR_BACKEND_LAMBDA_REGEX = ".+\\\$lambda-\\d+".toRegex()

    fun findSourceFileForClassIncludeLibrarySources(
        project: Project,
        scope: GlobalSearchScope,
        className: JvmClassName,
        fileName: String,
        location: Location? = null
    ): KtFile? {
        return runReadAction {
            findSourceFileForClass(
                project,
                listOf(scope, KotlinSourceFilterScope.librarySources(GlobalSearchScope.allScope(project), project)),
                className,
                fileName,
                location
            )
        }
    }

    fun findSourceFileForClass(
        project: Project,
        scopes: List<GlobalSearchScope>,
        className: JvmClassName,
        fileName: String,
        location: Location?
    ): KtFile? {
        if (!isKotlinSourceFile(fileName)) return null
        if (DumbService.getInstance(project).isDumb) return null

        val partFqName = className.fqNameForClassNameWithoutDollars

        for (scope in scopes) {
            val files = findFilesByNameInPackage(className, fileName, project, scope)

            if (files.isEmpty()) {
                continue
            }

            if (files.size == 1 && !forceRanking || location == null) {
                return files.first()
            }

            StaticFacadeIndexUtil.findFilesForFilePart(partFqName, scope, project)
                .singleOrNull { it.name == fileName }
                ?.let { return it }

            return FileRankingCalculatorForIde.findMostAppropriateSource(files, location)
        }

        return null
    }

    private fun findFilesByNameInPackage(
        className: JvmClassName,
        fileName: String,
        project: Project,
        searchScope: GlobalSearchScope
    ): List<KtFile> {
        val files = findFilesWithExactPackage(className.packageFqName, searchScope, project).filter { it.name == fileName }
        return files.sortedWith(JavaElementFinder.byClasspathComparator(searchScope))
    }

    fun isKotlinSourceFile(fileName: String): Boolean {
        val extension = FileUtilRt.getExtension(fileName).toLowerCase()
        return extension in KotlinFileTypeFactoryUtils.KOTLIN_EXTENSIONS
    }

    fun isKotlinFakeLineNumber(location: Location): Boolean {
        // The compiler inserts a fake line number for single-line inline function calls with a
        // lambda parameter such as:
        //
        //   42.let { it + 1 }
        //
        // This is done so that a breakpoint can be set on the lambda and on the line even though
        // the lambda is on the same line. When stepping, we do not want to stop at such fake line
        // numbers. They cause us to step to line 1 of the current file.
        try {
            if (location.lineNumber("Kotlin") == 1 &&
                location.sourcePath("Kotlin") == "kotlin/jvm/internal/FakeKt" &&
                location.sourceName("Kotlin") == "fake.kt"
            ) {
                return true
            }
        } catch (ignored: AbsentInformationException) {
        }
        return false
    }

    fun String.isGeneratedLambdaName() = matches(IR_BACKEND_LAMBDA_REGEX)
}
