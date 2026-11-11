// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import org.jetbrains.kotlin.analysis.api.imports.getDefaultImports
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

class JKImportStorage(targetPlatform: TargetPlatform, project: Project) {
    private val imports = mutableSetOf<FqName>()

    private val kaDefaultImports = targetPlatform.getDefaultImports(project)

    private val defaultImports: Set<ImportPath> =
        kaDefaultImports.defaultImports.mapTo(mutableSetOf()) { it.importPath }

    fun addImport(import: FqName) {
        if (isImportNeeded(import, allowSingleIdentifierImport = false)) {
            imports += import
        }
    }

    fun addImport(import: String) {
        addImport(FqName(import))
    }

    fun getImports(): Set<FqName> = imports

    private fun isImportNeeded(fqName: FqName, allowSingleIdentifierImport: Boolean = false): Boolean {
        val fqNameString = fqName.asString()
        if (!allowSingleIdentifierImport && fqNameString.count { it == '.' } < 1) return false
        // NullabilityAnnotationsConversion removes only these annotations
        if (fqName in NULLABLE_ANNOTATIONS || fqName in NOT_NULL_ANNOTATIONS) return false
        if (defaultImports.any { fqName.isImported(it) }) return false
        if (PLATFORM_CLASSES_MAPPED_TO_KOTLIN.any { it.matches(fqNameString) }) return false
        return true
    }

    fun isImportNeeded(fqName: String, allowSingleIdentifierImport: Boolean = false): Boolean =
        isImportNeeded(FqName(fqName), allowSingleIdentifierImport)

    fun isDefaultImport(fqName: String): Boolean {
        val name = FqName(fqName)
        return defaultImports.any { name.isImported(it) } && kaDefaultImports.excludedFromDefaultImports.none { name.isImported(it) }
    }

    companion object {
        internal val PLATFORM_CLASSES_MAPPED_TO_KOTLIN: Set<Regex> = setOf(
            Regex("kotlin\\.jvm\\.functions\\.Function[0-9]"),
            Regex("java\\.util\\.((Set)|(Collection)|(List)|(Map)|(Iterator))"),
            Regex("java\\.lang\\.((Throwable)|(Cloneable)|(Integer)|(String)|(Comparable)|(Object)|(CharSequence))")
        )

        private val JAVA_TYPE_WRAPPERS_WHICH_HAVE_CONFLICTS_WITH_KOTLIN_ONES = setOf(
            FqName(CommonClassNames.JAVA_LANG_BOOLEAN),
            FqName(CommonClassNames.JAVA_LANG_BYTE),
            FqName(CommonClassNames.JAVA_LANG_SHORT),
            FqName(CommonClassNames.JAVA_LANG_LONG),
            FqName(CommonClassNames.JAVA_LANG_FLOAT),
            FqName(CommonClassNames.JAVA_LANG_DOUBLE)
        )

        private val SHORT_NAMES = JAVA_TYPE_WRAPPERS_WHICH_HAVE_CONFLICTS_WITH_KOTLIN_ONES
            .map { it.shortName().identifier }
            .toSet()

        fun isImportNeededForCall(qualifiedExpression: KtQualifiedExpression): Boolean {
            val shortName = qualifiedExpression.getCalleeExpressionIfAny()?.text ?: return true
            if (shortName !in SHORT_NAMES) return true
            val fqName = qualifiedExpression.selectorExpression?.mainReference?.resolve()?.kotlinFqName ?: return true
            return fqName !in JAVA_TYPE_WRAPPERS_WHICH_HAVE_CONFLICTS_WITH_KOTLIN_ONES
        }
    }
}