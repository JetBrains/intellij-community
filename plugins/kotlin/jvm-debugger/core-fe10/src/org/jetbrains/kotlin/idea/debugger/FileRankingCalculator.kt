// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.sun.jdi.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator.Ranking.Companion.LOW
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator.Ranking.Companion.MAJOR
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator.Ranking.Companion.MINOR
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator.Ranking.Companion.NORMAL
import org.jetbrains.kotlin.idea.debugger.FileRankingCalculator.Ranking.Companion.ZERO
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinFileSelector
import org.jetbrains.kotlin.idea.debugger.base.util.safeArguments
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.varargParameterPosition
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import kotlin.jvm.internal.FunctionBase

object FileRankingCalculatorForIde : FileRankingCalculator() {
    override fun analyze(element: KtElement) = element.analyze(BodyResolveMode.PARTIAL)
}

abstract class FileRankingCalculator(private val checkClassFqName: Boolean = true) : KotlinFileSelector {
    abstract fun analyze(element: KtElement): BindingContext

    override suspend fun chooseMostApplicableFile(files: List<KtFile>, location: Location): KtFile {
        val fileWithRankings = rankFiles(files, location)
        val fileWithMaxScore = fileWithRankings.maxByOrNull { it.value }!!
        return fileWithMaxScore.key
    }

    suspend fun rankFiles(files: Collection<KtFile>, location: Location): Map<KtFile, Int> {
        assert(files.isNotEmpty())
        return files.associateWith { fileRankingSafe(it, location).value }
    }

    private class Ranking(val value: Int) : Comparable<Ranking> {
        companion object {
            val LOW = Ranking(-1000)
            val ZERO = Ranking(0)
            val MINOR = Ranking(1)
            val NORMAL = Ranking(5)
            val MAJOR = Ranking(10)

            fun minor(condition: Boolean) = if (condition) MINOR else ZERO
        }

        operator fun unaryMinus() = Ranking(-value)
        operator fun plus(other: Ranking) = Ranking(value + other.value)
        override fun compareTo(other: Ranking) = this.value - other.value
        override fun toString() = value.toString()
    }

    private fun collect(vararg conditions: Any): Ranking {
        return conditions
            .map { condition ->
                when (condition) {
                    is Boolean -> Ranking.minor(condition)
                    is Int -> Ranking(condition)
                    is Ranking -> condition
                    else -> error("Invalid condition type ${condition.javaClass.name}")
                }
            }.fold(ZERO) { sum, r -> sum + r }
    }

    private suspend fun rankingForClass(clazz: KtClassOrObject, fqName: String, virtualMachine: VirtualMachine): Ranking {
        val bindingContext = smartReadAction(clazz.project) { analyze(clazz) }
        val descriptor = bindingContext[BindingContext.CLASS, clazz] ?: return ZERO

        val jdiType = virtualMachine.classesByName(fqName).firstOrNull() ?: run {
            // Check at least the class name if not found
            return readAction { rankingForClassName(fqName, descriptor, bindingContext) }
        }

        return rankingForClass(clazz, jdiType)
    }

    private suspend fun rankingForClass(clazz: KtClassOrObject, type: ReferenceType): Ranking {
        val bindingContext = smartReadAction(clazz.project) { analyze(clazz) }
        val descriptor = bindingContext[BindingContext.CLASS, clazz] ?: return ZERO

        return collect(
            readAction { rankingForClassName(type.name(), descriptor, bindingContext) },
            Ranking.minor(type.isAbstract && readAction { descriptor.modality } == Modality.ABSTRACT),
            Ranking.minor(type.isFinal && readAction { descriptor.modality } == Modality.FINAL),
            Ranking.minor(type.isStatic && readAction { !descriptor.isInner }),
            rankingForVisibility(descriptor, type)
        )
    }

    private fun rankingForClassName(fqName: String, descriptor: ClassDescriptor, bindingContext: BindingContext): Ranking {
        if (DescriptorUtils.isLocal(descriptor)) return ZERO

        val expectedFqName = makeTypeMapper(bindingContext).mapType(descriptor).className
        return when {
            checkClassFqName -> if (expectedFqName == fqName) MAJOR else LOW
            else -> if (expectedFqName.simpleName() == fqName.simpleName()) MAJOR else LOW
        }
    }

    private suspend fun rankingForMethod(function: KtFunction, method: Method): Ranking {
        val bindingContext = smartReadAction(function.project) { analyze(function) }
        val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, function] as? CallableMemberDescriptor ?: return ZERO

        if (function !is KtConstructor<*> && method.name() != descriptor.name.asString())
            return LOW

        return collect(
            method.isConstructor && function is KtConstructor<*>,
            method.isAbstract && readAction { descriptor.modality } == Modality.ABSTRACT,
            method.isFinal && readAction { descriptor.modality } == Modality.FINAL,
            method.isVarArgs && readAction { descriptor.varargParameterPosition() } >= 0,
            rankingForVisibility(descriptor, method),
            readAction { descriptor.valueParameters.size } == (method.safeArguments()?.size ?: 0)
        )
    }

    private suspend fun rankingForAccessor(accessor: KtPropertyAccessor, method: Method): Ranking {
        val methodName = method.name()
        val expectedPropertyName = readAction { accessor.property.name } ?: return ZERO

        if (accessor.isSetter) {
            if (!methodName.startsWith("set") || method.returnType() !is VoidType || method.argumentTypes().size != 1)
                return -MAJOR
        }

        if (accessor.isGetter) {
            if (!methodName.startsWith("get") && !methodName.startsWith("is"))
                return -MAJOR
            else if (method.returnType() is VoidType || method.argumentTypes().isNotEmpty())
                return -NORMAL
        }

        val actualPropertyName = getPropertyName(methodName, accessor.isSetter)
        return if (expectedPropertyName == actualPropertyName) NORMAL else -NORMAL
    }

    private fun getPropertyName(accessorMethodName: String, isSetter: Boolean): String {
        if (isSetter) {
            return accessorMethodName.drop(3)
        }

        return accessorMethodName.drop(if (accessorMethodName.startsWith("is")) 2 else 3)
    }

    private suspend fun rankingForProperty(property: KtProperty, method: Method): Ranking {
        val methodName = method.name()
        val propertyName = readAction { property.name } ?: return ZERO

        if (readAction { property.isTopLevel } && method.name() == "<clinit>") {
            // For top-level property initializers
            return MINOR
        }

        if (!methodName.startsWith("get") && !methodName.startsWith("set"))
            return -MAJOR

        // boolean is
        return if (methodName.drop(3) == propertyName.capitalizeAsciiOnly()) MAJOR else -NORMAL
    }

    private suspend fun rankingForVisibility(descriptor: DeclarationDescriptorWithVisibility, accessible: Accessible): Ranking {
        val visibility = readAction { descriptor.visibility }
        return collect(
            accessible.isPublic && visibility == DescriptorVisibilities.PUBLIC,
            accessible.isProtected && visibility == DescriptorVisibilities.PROTECTED,
            accessible.isPrivate && visibility == DescriptorVisibilities.PRIVATE
        )
    }

    private suspend fun fileRankingSafe(file: KtFile, location: Location): Ranking {
        return try {
            fileRanking(file, location)
        } catch (e: ClassNotLoadedException) {
            LOG.error("ClassNotLoadedException should never happen in FileRankingCalculator", e)
            ZERO
        } catch (_: AbsentInformationException) {
            ZERO
        } catch (_: InternalException) {
            ZERO
        } catch (e: VMDisconnectedException) {
            throw e
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            LOG.error("Exception during Kotlin sources ranking", e)
            ZERO
        }
    }

    private suspend fun fileRanking(file: KtFile, location: Location): Ranking {
        val locationLineNumber = location.lineNumber() - 1
        val lineStartOffset = readAction { file.getLineStartOffset(locationLineNumber) } ?: return LOW
        val elementAt = readAction { file.findElementAt(lineStartOffset) } ?: return ZERO

        var overallRanking = ZERO
        val method = location.method()

        if (method.isIndyLambda() || method.isAnonymousClassLambda()) {
            val (className, methodName) = method.getContainingClassAndMethodNameForLambda() ?: return ZERO
            if (method.isBridge && method.isSynthetic) {
                // It might be a static lambda field accessor
                val containingClass = readAction { elementAt.getParentOfType<KtClassOrObject>(false) } ?: return LOW
                return rankingForClass(containingClass, className, location.virtualMachine())
            } else {
                val containingFunctionLiteral =
                    readAction { findFunctionLiteralOnLine(elementAt) }
                        ?: readAction { findAnonymousFunctionInParent(elementAt) }
                        ?: return LOW

                val containingCallable = readAction { findNonLocalCallableParent(containingFunctionLiteral) } ?: return LOW
                when (containingCallable) {
                    is KtFunction -> if (readAction { containingCallable.name } == methodName) overallRanking += MAJOR
                    is KtProperty -> if (readAction { containingCallable.name } == methodName) overallRanking += MAJOR
                    is KtPropertyAccessor -> if (readAction { containingCallable.property.name } == methodName) overallRanking += MAJOR
                }

                val containingClass = readAction { containingCallable.getParentOfType<KtClassOrObject>(false) }
                if (containingClass != null) {
                    overallRanking += rankingForClass(containingClass, className, location.virtualMachine())
                }

                return overallRanking
            }
        }

        // TODO support <clinit>
        if (method.name() == "<init>") {
            val containingClass = readAction { elementAt.getParentOfType<KtClassOrObject>(false) } ?: return LOW
            val constructorOrInitializer = readAction {
                elementAt.getParentOfTypes2<KtConstructor<*>, KtClassInitializer>()?.takeIf { containingClass.isAncestor(it) }
                    ?: containingClass.primaryConstructor?.takeIf { it.getLine() == containingClass.getLine() }
            }
            if (constructorOrInitializer == null
                && locationLineNumber < readAction { containingClass.getLine() }
                && locationLineNumber > readAction { containingClass.lastChild.getLine() }
            ) {
                return LOW
            }

            overallRanking += rankingForClass(containingClass, location.declaringType())

            if (constructorOrInitializer is KtConstructor<*>)
                overallRanking += rankingForMethod(constructorOrInitializer, method)
        } else {
            val callable = readAction { findNonLocalCallableParent(elementAt) } ?: return LOW
            overallRanking += when (callable) {
                is KtFunction -> rankingForMethod(callable, method)
                is KtPropertyAccessor -> rankingForAccessor(callable, method)
                is KtProperty -> rankingForProperty(callable, method)
                else -> return LOW
            }

            val containingClass = readAction { elementAt.getParentOfType<KtClassOrObject>(false) }
            if (containingClass != null)
                overallRanking += rankingForClass(containingClass, location.declaringType())
        }

        return overallRanking
    }

    private fun findFunctionLiteralOnLine(element: PsiElement): KtFunctionLiteral? {
        val literal = element.getParentOfType<KtFunctionLiteral>(false)
        if (literal != null) {
            return literal
        }

        val callExpression = element.getParentOfType<KtCallExpression>(false) ?: return null

        for (lambdaArgument in callExpression.lambdaArguments) {
            if (element.getLine() == lambdaArgument.getLine()) {
                val functionLiteral = lambdaArgument.getLambdaExpression()?.functionLiteral
                if (functionLiteral != null) {
                    return functionLiteral
                }
            }
        }

        return null
    }

    private fun findAnonymousFunctionInParent(element: PsiElement): KtNamedFunction? {
        val parentFun = element.getParentOfType<KtNamedFunction>(false)
        if (parentFun != null && parentFun.isFunctionalExpression()) {
            return parentFun
        }
        return null
    }

    private tailrec fun findNonLocalCallableParent(element: PsiElement): PsiElement? {
        fun PsiElement.isCallableDeclaration() = this is KtProperty || this is KtFunction || this is KtAnonymousInitializer

        // org.jetbrains.kotlin.psi.KtPsiUtil.isLocal
        fun PsiElement.isLocalDeclaration(): Boolean {
            val containingDeclaration = getParentOfType<KtDeclaration>(true)
            return containingDeclaration is KtCallableDeclaration || containingDeclaration is KtPropertyAccessor
        }

        if (element.isCallableDeclaration() && !element.isLocalDeclaration()) {
            return element
        }

        val containingCallable = element.getParentOfTypes3<KtProperty, KtFunction, KtAnonymousInitializer>()
            ?: return null

        if (containingCallable.isLocalDeclaration()) {
            return findNonLocalCallableParent(containingCallable)
        }

        return containingCallable
    }

    private fun Method.getContainingClassAndMethodNameForLambda(): Pair<String, String>? {
        if (isIndyLambda()) {
            return getContainingClassAndMethodNameForIndyLambda()
        }
        // TODO this breaks nested classes
        val declaringClass = declaringType() as ClassType
        val (className, methodName) = declaringClass.name().split('$', limit = 3)
            .takeIf { it.size == 3 }
            ?: return null

        return Pair(className, methodName)
    }

    private fun Method.isAnonymousClassLambda(): Boolean {
        val declaringClass = declaringType() as? ClassType ?: return false

        tailrec fun ClassType.isLambdaClass(): Boolean {
            if (interfaces().any { it.name() == FunctionBase::class.java.name }) {
                return true
            }

            val superClass = superclass() ?: return false
            return superClass.isLambdaClass()
        }

        return declaringClass.superclass()?.isLambdaClass() == true
    }

    private fun Method.isIndyLambda(): Boolean {
        return name().matches(INDY_LAMBDA_NAME_REGEX)
    }

    private fun Method.getContainingClassAndMethodNameForIndyLambda(): Pair<String, String>? {
        val match = INDY_LAMBDA_NAME_REGEX.matchEntire(this.name()) ?: return null
        val values = match.groupValues
        if (values.size != 2) {
            return null
        }
        val methodName = values[1]
        val className = this.declaringType().name()
        return className to methodName
    }

    private fun makeTypeMapper(bindingContext: BindingContext): KotlinTypeMapper {
        return KotlinTypeMapper(
            bindingContext,
            ClassBuilderMode.LIGHT_CLASSES,
            "debugger",
            KotlinTypeMapper.LANGUAGE_VERSION_SETTINGS_DEFAULT, // TODO use proper LanguageVersionSettings
            useOldInlineClassesManglingScheme = false
        )
    }

    companion object {
        private val INDY_LAMBDA_NAME_REGEX = "([^\$]+)\\\$lambda\\\$\\d+.*".toRegex()

        val LOG = Logger.getInstance("FileRankingCalculator")
    }
}

private fun String.simpleName() = substringAfterLast('.').substringAfterLast('$')

private fun PsiElement.getLine(): Int {
    return DiagnosticUtils.getLineAndColumnInPsiFile(containingFile, textRange).line
}
