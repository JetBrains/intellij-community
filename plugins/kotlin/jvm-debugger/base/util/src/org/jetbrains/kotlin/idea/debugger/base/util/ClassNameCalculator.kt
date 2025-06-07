// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.Stack
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.util.*

/**
 * JVM backend requires files to be analyzed against JVM-targeted libraries, so calling it from common modules
 * (that depend on common libraries) leads to unpredictable results. Also, JVM backend initialization is quite expensive
 * as it calculates a lot more information than the debugger needs.
 *
 * `ClassNameCalculator` aims to fix breakpoints in common modules. It's somehow similar to Ultra Light Classes – it also doesn't depend
 * on the backend. In case if all goes wrong, there's a registry key available that turns off the new behavior.
 *
 * The default implementation can be extended using the [EP_NAME]:
 * Later extensions will overwrite previous extensions.
 */
interface ClassNameCalculator {
    companion object : ClassNameCalculator {
        @JvmStatic
        fun getInstance(): ClassNameCalculator {
            return CompositeClassNameCalculator(listOf(DefaultClassNameCalculator) + EP_NAME.extensionList)
        }

        internal val EP_NAME = ExtensionPointName<ClassNameCalculator>("org.jetbrains.kotlin.idea.debugger.base.util.classNameCalculator")

        override fun getClassName(element: KtElement): String? {
            return getInstance().getClassName(element)
        }

        override fun getClassNames(file: KtFile): Map<KtElement, String> {
            return getInstance().getClassNames(file)
        }

        override fun getTopLevelNames(file: KtFile): List<String> {
            return getInstance().getTopLevelNames(file)
        }
    }

    @RequiresReadLock(generateAssertion = false)
    fun getClassNames(file: KtFile): Map<KtElement, String>

    @RequiresReadLock(generateAssertion = false)
    fun getClassName(element: KtElement): String? = null

    /**
     * The returned list should include all top-level FQNs declared in the file, including classes, scripts and file FQNs.
     * The list may also include inner classes, but it is not guarantied.
     */
    fun getTopLevelNames(file: KtFile): List<String> = getClassNames(file).values.distinct()
}

/**
 * Composite implementation of the [ClassNameCalculator]
 * Note: the order of [instances] matters! Later instances will override results from previous ones.
 * (It is expected that the first instance is a default implementation, the following instances are additional extensions)
 */
private class CompositeClassNameCalculator(
    private val instances: List<ClassNameCalculator>
) : ClassNameCalculator {
    override fun getClassNames(file: KtFile): Map<KtElement, String> {
        return WeakHashMap<KtElement, String>().apply {
            instances.forEach { classNameCalculator -> putAll(classNameCalculator.getClassNames(file)) }
        }
    }

    override fun getClassName(element: KtElement): String? {
        /* Note: Later instances win over earlier one's: We're therefore asking the implementations from back to front */
        return instances.reversed().firstNotNullOfOrNull { classNameCalculator -> classNameCalculator.getClassName(element) }
    }

    override fun getTopLevelNames(file: KtFile): List<String> {
        return instances.flatMap { it.getTopLevelNames(file) }
    }
}

private object DefaultClassNameCalculator : ClassNameCalculator {
    override fun getClassNames(file: KtFile): Map<KtElement, String> {
        return CachedValuesManager.getCachedValue(file) {
            val visitor = ClassNameCalculatorVisitor()
            file.accept(visitor)
            CachedValueProvider.Result(visitor.allNames, file)
        }
    }

    override fun getClassName(element: KtElement): String? {
        val target = when (element) {
            is KtFunctionLiteral -> element.parent as? KtLambdaExpression ?: element
            else -> element
        }

        return getClassNames(element.containingKtFile)[target]
    }

    override fun getTopLevelNames(file: KtFile): List<String> {
        return CachedValuesManager.getCachedValue(file) {
            val declarations = file.declarations
            val fileName = getFileFQN(file)
            val classNames = declarations.filterIsInstance<KtClassLikeDeclaration>().mapNotNull { it.getClassId()?.asString() }
            val scriptNames = declarations.filterIsInstance<KtScript>().map { it.fqName.asString() }
            val allNames = classNames + scriptNames + listOf(fileName)
            CachedValueProvider.Result(allNames, file)
        }
    }
}

private class ClassNameCalculatorVisitor() : KtTreeVisitorVoid() {
    private val names = Stack<String?>()
    private val anonymousIndices = Stack<Int>()
    private var collectedNames = WeakHashMap<KtElement, String>()

    val allNames: Map<KtElement, String>
        get() = collectedNames

    override fun visitKtFile(file: KtFile) {
        saveName(file, getFileFQN(file))
        super.visitKtFile(file)
    }


    override fun visitScript(script: KtScript) {
        push(script, script.fqName.asString())
        super.visitScript(script)
        pop()
    }

    override fun visitClassOrObject(klass: KtClassOrObject) {
        if (klass.name == null) {
            // Containing object literal is already in stack
            super.visitClassOrObject(klass)
            return
        }

        push(klass, if (klass.isTopLevel()) klass.fqName?.asString() else klass.name)
        super.visitClassOrObject(klass)
        pop()
    }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        push(expression, nextAnonymousName())
        super.visitObjectLiteralExpression(expression)
        pop()
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression) {
        push(expression, nextAnonymousName())
        super.visitLambdaExpression(expression)
        pop()
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        push(expression, nextAnonymousName())
        super.visitCallableReferenceExpression(expression)
        pop()
    }

    override fun visitProperty(property: KtProperty) {
        push(property, if (property.isTopLevel) getTopLevelName(property) else property.name, recordName = false)

        if (property.hasDelegate()) {
            nextAnonymousName() // Extra $1 closure for properties with delegates
        }

        super.visitProperty(property)
        pop()
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        val isLocal = function.isLocal
        val isFunctionLiteral = isLocal && function.name == null
        val name = when {
            function.isTopLevel -> getTopLevelName(function)
            isFunctionLiteral -> nextAnonymousName()
            // It is nextAnonymousName() for local functions too in the old backend,
            // but in the IR it is compiled into a class function named as <OUTER_NAME>$<LOCAL_NAME>
            else -> function.name
        }

        push(function, name, recordName = isFunctionLiteral)

        if (function.hasModifier(KtTokens.SUSPEND_KEYWORD) && !isLocal) {
            nextAnonymousName() // Extra $1 closure for suspend functions
        }

        super.visitNamedFunction(function)
        pop()
    }

    private fun getTopLevelName(declaration: KtDeclaration): String? {
        val selfName = declaration.name ?: return null
        val info = JvmFileClassUtil.getFileClassInfoNoResolve(declaration.containingKtFile)
        return info.facadeClassFqName.asString() + "$" + selfName
    }

    private fun push(element: KtElement, name: String?, recordName: Boolean = true) {
        names.push(name)
        anonymousIndices.push(0)
        if (recordName) {
            saveName(element, names.joinToString("$"))
        }
    }

    private fun saveName(element: KtElement, name: String) {
        collectedNames[element] = name
    }

    private fun pop() {
        names.pop()
        anonymousIndices.pop()
    }

    private fun nextAnonymousName(): String {
        val index = anonymousIndices.pop() + 1
        anonymousIndices.push(index)
        return index.toString()
    }
}

private fun getFileFQN(file: KtFile): String = JvmFileClassUtil.getFileClassInfoNoResolve(file).fileClassFqName.asString()
