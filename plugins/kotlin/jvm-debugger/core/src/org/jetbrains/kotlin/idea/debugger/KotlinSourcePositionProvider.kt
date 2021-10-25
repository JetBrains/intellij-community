// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.LocalVariableDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.load.java.possibleGetMethodNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi

class KotlinSourcePositionProvider : SourcePositionProvider() {
    override fun computeSourcePosition(
        descriptor: NodeDescriptor,
        project: Project,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        if (context.frameProxy == null) return null

        return when(descriptor) {
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

        val contextElement = getContextElement(place) ?: return null

        val codeFragment = KtPsiFactory(context.project).createExpressionCodeFragment(descriptor.name, contextElement)
        val expression = codeFragment.getContentElement()
        if (expression is KtSimpleNameExpression) {
            val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
            val declarationDescriptor = BindingContextUtils.extractVariableDescriptorFromReference(bindingContext, expression)
            val sourceElement = declarationDescriptor?.source
            if (sourceElement is KotlinSourceElement) {
                val element = sourceElement.getPsi() ?: return null
                if (nearest) {
                    return DebuggerContextUtil.findNearest(context, element, element.containingFile)
                }
                return SourcePosition.createFromOffset(element.containingFile, element.textOffset)
            }
        }

        return null
    }

    private fun computeSourcePositionForDeclaration(
        name: String,
        declaringType: ReferenceType,
        context: DebuggerContextImpl,
        nearest: Boolean
    ) = computeSourcePositionForDeclaration(declaringType, context, nearest) {
        it.name == name
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

        if (fieldName == AsmUtil.CAPTURED_THIS_FIELD
            || fieldName == AsmUtil.CAPTURED_RECEIVER_FIELD
            || fieldName.startsWith(AsmUtil.LABELED_THIS_FIELD)
        ) {
            return null
        }

        return computeSourcePositionForDeclaration(fieldName, descriptor.field.declaringType(), context, nearest)
    }

    private fun computeSourcePosition(
        descriptor: GetterDescriptor,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition?  {
        val name = descriptor.name
        val type = descriptor.getter.declaringType()
        computeSourcePositionForPropertyDeclaration(name, type, context, nearest)?.let { return it }

        if (type is ClassType) {
            val interfaces = type.safeAllInterfaces().distinct()
            for (intf in interfaces) {
                computeSourcePositionForPropertyDeclaration(name, intf, context, nearest)?.let { return it }
            }
        }
        return null
    }

    private fun findClassByType(project: Project, type: ReferenceType, context: DebuggerContextImpl): PsiElement? {
        val session = context.debuggerSession
        val scope = session?.searchScope ?: GlobalSearchScope.allScope(project)
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
        val debugProcess = context.debugProcess
        if (debugProcess != null) {
            try {
                val locations = type.safeAllLineLocations()
                if (locations.isNotEmpty()) {
                    val lastLocation = locations[locations.size - 1]
                    return debugProcess.positionManager.getSourcePosition(lastLocation)
                }
            } catch (ignored: AbsentInformationException) {
            } catch (ignored: ClassNotPreparedException) {
            }
        }
        return null
    }
}
