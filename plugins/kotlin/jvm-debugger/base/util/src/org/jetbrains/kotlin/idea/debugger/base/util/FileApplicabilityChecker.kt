// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parents
import com.sun.jdi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


@ApiStatus.Internal
interface KotlinFileSelector {
    suspend fun chooseMostApplicableFile(files: List<KtFile>, location: Location): KtFile
}

@ApiStatus.Internal
object FileApplicabilityChecker : KotlinFileSelector {
    enum class Applicability {
        NO, UNCERTAIN, YES
    }

    private class ApplicabilityContext(
        val file: KtFile,
        val classNames: Map<KtElement, String>
    )

    override suspend fun chooseMostApplicableFile(files: List<KtFile>, location: Location): KtFile {
        if (files.size == 1) {
            return files.first()
        }

        return files.maxByOrNull { getApplicability(it, location) }
            ?: error("One or more source files expected")
    }

    private suspend fun getApplicability(file: KtFile, location: Location): Applicability {
        try {
            val context = readAction { ApplicabilityContext(file, ClassNameCalculator.getClassNames(file)) }
            return context.getApplicability(location)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: ClassNotLoadedException) {
            return Applicability.UNCERTAIN
        } catch (_: AbsentInformationException) {
            return Applicability.UNCERTAIN
        } catch (_: InternalException) {
            return Applicability.UNCERTAIN
        }
    }

    private suspend fun ApplicabilityContext.getApplicability(location: Location): Applicability {
        val sourceLineNumber = location.lineNumber() - 1
        if (sourceLineNumber < 0) return Applicability.NO
        val declaringType = location.declaringType()
        val typeName = declaringType.name()
        val methodName = location.method().name()
        val isCallableReferenceClass = declaringType is ClassType && isCallableReferenceClass(declaringType)
        val isFunctionLiteralClass = declaringType is ClassType && isFunctionLiteralClass(declaringType)

        return readAction { getApplicability(typeName, methodName, sourceLineNumber, isCallableReferenceClass, isFunctionLiteralClass) }
    }

    private fun ApplicabilityContext.getApplicability(
        typeName: String,
        methodName: String,
        sourceLineNumber: Int,
        isCallableReferenceClass: Boolean,
        isFunctionLiteralClass: Boolean
    ): Applicability {
        val rangeOfLine = file.getRangeOfLine(sourceLineNumber) ?: return Applicability.NO
        if (isCallableReferenceClass) {
            return getApplicabilityByClassName(rangeOfLine, typeName, KtCallableReferenceExpression::class.java)
        } else if (isFunctionLiteralClass) {
            return getApplicabilityByClassName(rangeOfLine, typeName, KtLambdaExpression::class.java)
        }

        if (methodName == "<init>" || methodName == "<clinit>") {
            return getApplicabilityByClassName(rangeOfLine, typeName, KtClassOrObject::class.java)
        }

        val lineStartOffset = file.getLineStartOffset(sourceLineNumber) ?: return Applicability.NO
        val elementAt = file.findElementAt(lineStartOffset) ?: return Applicability.NO
        val callableParents = elementAt.parents(withSelf = true).filter {
            it is KtCallableDeclaration || it is KtPropertyAccessor
        }
        for (declaration in callableParents) {
            if (declaration !is KtDeclaration) continue
            val classParent = findClassParent(declaration) ?: file
            if (classNames[classParent] != typeName) {
                return Applicability.NO
            }

            val name = when (declaration) {
                is KtPropertyAccessor -> CallableNameCalculator.getAccessorName(declaration.property, declaration.isSetter)
                is KtProperty -> CallableNameCalculator.getAccessorName(declaration, JvmAbi.isSetterName(methodName))
                is KtNamedFunction -> CallableNameCalculator.getFunctionName(declaration)
                else -> null
            }

            if (name != null && name.matches(methodName)) {
                return Applicability.YES
            }
        }

        return Applicability.UNCERTAIN
    }

    private fun findClassParent(declaration: KtDeclaration): KtElement? {
        val parent = declaration.getParentOfType<KtClassOrObject>(strict = true) ?: return null
        if (parent is KtObjectDeclaration && parent.name == null) {
            return (parent.parent as? KtObjectLiteralExpression) ?: parent
        }
        return parent
    }

    private fun ApplicabilityContext.getApplicabilityByClassName(
        rangeOfLine: TextRange,
        typeName: String,
        clazz: Class<out KtElement>
    ): Applicability {
        for (declaration in file.findElementsOfTypeInRange(rangeOfLine, clazz)) {
            val expectedClassName = classNames[declaration] ?: continue
            if (expectedClassName == typeName) {
                return Applicability.YES
            }
        }

        return Applicability.NO
    }

    private fun isFunctionLiteralClass(type: ClassType) = type.hasInterface("kotlin.jvm.internal.FunctionBase")
    private fun isCallableReferenceClass(type: ClassType) = type.hasSuperClass("kotlin.jvm.internal.FunctionReference")
}
