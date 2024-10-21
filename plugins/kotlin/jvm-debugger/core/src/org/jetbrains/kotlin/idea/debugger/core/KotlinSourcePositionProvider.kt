// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.LocalVariableDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameterSymbol
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.CAPTURED_RECEIVER_FIELD
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.CAPTURED_THIS_FIELD
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants.LABELED_THIS_FIELD
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllInterfaces
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.core.render.GetterDescriptor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.possibleGetMethodNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

class KotlinSourcePositionProvider : SourcePositionProvider() {
    override fun computeSourcePosition(
        descriptor: NodeDescriptor,
        project: Project,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        if (context.frameProxy == null || DumbService.isDumb(project)) return null

        return when (descriptor) {
            is FieldDescriptor -> computeSourcePosition(descriptor, context, nearest)
            is GetterDescriptor -> computeSourcePosition(descriptor, context, nearest)
            is LocalVariableDescriptor -> computeSourcePosition(descriptor, context, nearest)
            else -> null
        }
    }

    private fun computeSourcePosition(
        descriptor: LocalVariableDescriptor,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        val place = PositionUtil.getContextElement(context) ?: return null
        if (place.containingFile !is KtFile) return null

        val contextElement = CodeFragmentContextTuner.getInstance().tuneContextElement(place) ?: return null
        val codeFragment = KtPsiFactory(context.project).createExpressionCodeFragment(descriptor.name, contextElement)
        val localReferenceExpression = codeFragment.getContentElement()

        if (localReferenceExpression !is KtSimpleNameExpression) return null

        return runDumbAnalyze(localReferenceExpression, fallback = null) f@ {
            for (symbol in localReferenceExpression.mainReference.resolveToSymbols()) {
                if (symbol !is KaVariableSymbol) continue

                if (symbol is KaValueParameterSymbol && symbol.isImplicitLambdaParameter) {
                    // symbol.psi is null or lambda, so we need a bit more work to find nearest position.
                    val lambda = symbol.getFunctionLiteralByImplicitLambdaParameterSymbol() ?: continue
                    return@f when {
                        nearest -> DebuggerContextUtil.findNearest(context, lambda.containingFile) { _ -> implicitLambdaParameterUsages(lambda) }
                        else -> SourcePosition.createFromOffset(lambda.containingFile, lambda.lBrace.textOffset)
                    }
                }

                symbol.psi?.let { element ->
                    return@f when {
                        nearest -> DebuggerContextUtil.findNearest(context, element, element.containingFile)
                        else -> SourcePosition.createFromOffset(element.containingFile, element.textOffset)
                    }
                }
            }
            null
        }
    }

    private fun implicitLambdaParameterUsages(lambda: KtFunctionLiteral): List<TextRange> {
        return buildList {
            lambda.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                        if (expression is KtNameReferenceExpression && expression.getFunctionLiteralByImplicitLambdaParameter() == lambda) {
                            add(expression.textRange)
                        }
                    }
                }
            )
        }
    }

    private fun computeSourcePositionForPropertyDeclaration(
        name: String,
        declaringType: ReferenceType,
        context: DebuggerContextImpl,
        nearest: Boolean
    ) = computeSourcePositionForDeclaration(declaringType, context, nearest) {
        it.name == name || it.name in name.getPropertyDeclarationNameVariations()
    }

    private fun String.getPropertyDeclarationNameVariations(): List<String> {
        val name = Name.guessByFirstCharacter(this)
        return possibleGetMethodNames(name).map(Name::asString)
    }

    private fun computeSourcePositionForDeclaration(
        declaringType: ReferenceType,
        context: DebuggerContextImpl,
        nearest: Boolean,
        declarationSelector: (KtDeclaration) -> Boolean
    ): SourcePosition? {
        val myClass = findClassByType(context.project, declaringType, context)?.navigationElement as? KtClassOrObject ?: return null
        val declaration = myClass.declarations.firstOrNull(declarationSelector) ?: return null

        if (nearest) {
            return DebuggerContextUtil.findNearest(context, declaration, myClass.containingFile)
        }
        return SourcePosition.createFromOffset(declaration.containingFile, declaration.textOffset)
    }

    private fun computeSourcePosition(
        descriptor: FieldDescriptor,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        val fieldName = descriptor.field.name()

        if (fieldName == CAPTURED_THIS_FIELD
            || fieldName == CAPTURED_RECEIVER_FIELD
            || fieldName.startsWith(LABELED_THIS_FIELD)
        ) {
            return null
        }

        return computeSourcePositionForDeclaration(descriptor.field.declaringType(), context, nearest) {
            it.name == fieldName
        }
    }

    private fun computeSourcePosition(
        descriptor: GetterDescriptor,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        val name = descriptor.name
        val type = descriptor.getter.declaringType()
        computeSourcePositionForPropertyDeclaration(name, type, context, nearest)?.let { return it }

        if (type is ClassType) {
            val superInterfaceTypes = type.safeAllInterfaces().distinct()
            for (interfaceType in superInterfaceTypes) {
                computeSourcePositionForPropertyDeclaration(name, interfaceType, context, nearest)?.let { return it }
            }
        }

        return null
    }

    private fun findClassByType(project: Project, type: ReferenceType, context: DebuggerContextImpl): PsiElement? {
        val scope = context.debuggerSession?.searchScope ?: GlobalSearchScope.allScope(project)
        val className = JvmClassName.byInternalName(type.name()).fqNameForClassNameWithoutDollars.asString()

        val myClass = JavaPsiFacade.getInstance(project).findClass(className, scope)
        if (myClass != null) return myClass

        val position = getLastSourcePosition(type, context)
        if (position != null) {
            val element = position.elementAt
            if (element != null) {
                return element.getStrictParentOfType<KtClassOrObject>()
            }
        }

        return null
    }

    private fun getLastSourcePosition(type: ReferenceType, context: DebuggerContextImpl): SourcePosition? {
        val debugProcess = context.debugProcess ?: return null

        try {
            val locations = type.safeAllLineLocations()
            if (locations.isNotEmpty()) {
                val lastLocation = locations[locations.size - 1]
                return debugProcess.positionManager.getSourcePosition(lastLocation)
            }
        } catch (ignored: AbsentInformationException) {
        } catch (ignored: ClassNotPreparedException) {
        }

        return null
    }
}