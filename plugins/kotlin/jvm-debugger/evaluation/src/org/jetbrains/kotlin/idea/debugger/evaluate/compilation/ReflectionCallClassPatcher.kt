// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.SignatureParsing
import com.intellij.psi.impl.compiled.StubBuildingVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.box
import org.jetbrains.org.objectweb.asm.*

object ReflectionCallClassPatcher {
    var isEnabled: Boolean
        get() = Registry.`is`("kotlin.debugger.evaluator.enable.reflection.patching")
        set(newValue) {
            Registry.get("kotlin.debugger.evaluator.enable.reflection.patching").setValue(newValue)
        }

    @RequiresReadLock
    fun patch(bytes: ByteArray, project: Project, scope: GlobalSearchScope): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)

        reader.accept(object : ClassVisitor(Opcodes.API_VERSION, writer) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                ProgressManager.checkCanceled()

                val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                return ReflectionCallMethodVisitor(project, scope, delegate)
            }
        }, 0)

        return writer.toByteArray()
    }
}

private class ReflectionCallMethodVisitor(
    private val project: Project,
    private val scope: GlobalSearchScope,
    methodVisitor: MethodVisitor
) : MethodVisitor(Opcodes.API_VERSION, methodVisitor) {
    private fun boxValue(type: Type, boxedType: Type) {
        val methodDescriptor = "(" + type.descriptor + ")" + boxedType.descriptor
        super.visitMethodInsn(Opcodes.INVOKESTATIC, boxedType.internalName, "valueOf", methodDescriptor, false)
    }

    private fun unboxValue(type: Type, boxedType: Type) {
        val boxedClassName = boxedType.internalName
        val methodName = EvaluatorValueConverter.UNBOXING_METHOD_NAMES[boxedClassName] ?: error("Unexpected boxed type: $boxedType")
        val methodDescriptor = "()" + type.descriptor
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxedClassName, methodName, methodDescriptor, false)
    }

    private fun pushClassLiteral(type: Type, resolvedClass: PsiClass? = null) {
        if (type.sort != Type.OBJECT && type.sort != Type.ARRAY) {
            throw IllegalStateException("Object or array type expected, got $type")
        }

        val internalName = type.internalName
        val psiClass = resolvedClass ?: findClass(internalName)

        if (psiClass != null && !psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            super.visitLdcInsn(internalName.replace('/', '.'))
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
        } else {
            super.visitLdcInsn(type)
        }
    }

    private fun findClass(internalName: String): PsiClass? {
        val type = Type.getObjectType(internalName)
        if (type.sort != Type.OBJECT) {
            // Array doesn't have any fields (`length` calls is transformed to the ARRAYLENGTH instruction)
            return null
        }

        val iterator = SignatureParsing.CharIterator(type.descriptor)
        val classType = SignatureParsing.parseTypeStringToTypeInfo(iterator, StubBuildingVisitor.GUESSING_PROVIDER).text()
        return JavaPsiFacade.getInstance(project).findClass(classType, scope)
    }

    private fun fetchReflectionField(owner: String, name: String) {
        pushClassLiteral(Type.getObjectType(owner)) /// ..., receiverType
        super.visitLdcInsn(name) /// ..., receiverType, fieldName

        val methodSignature = "(Ljava/lang/String;)Ljava/lang/reflect/Field;"
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", methodSignature, false) /// ..., field
        super.visitInsn(Opcodes.DUP) /// ..., field, field
        super.visitLdcInsn(1) /// ..., field, field, 'true'
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false) /// ..., field
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        val field = findClass(owner)?.findFieldByName(name, false)
        if (field == null || field.hasModifierProperty(PsiModifier.PUBLIC)) {
            return super.visitFieldInsn(opcode, owner, name, descriptor)
        }

        val valueType = Type.getType(descriptor)
        val boxedValueType = box(valueType)

        fetchReflectionField(owner, name) // ..., field

        val isCategory2Type = valueType.size == 2

        when (opcode) {
            Opcodes.GETSTATIC -> {
                /// field
                super.visitInsn(Opcodes.ACONST_NULL) /// field, receiver
            }
            Opcodes.PUTSTATIC -> {
                /// value, field
                super.visitInsn(Opcodes.ACONST_NULL) /// value, field, receiver
                super.visitInsn(if (isCategory2Type) Opcodes.DUP2_X2 else Opcodes.DUP2_X1) /// field, receiver, value, field, receiver
                super.visitInsn(Opcodes.POP2) /// field, receiver, value
            }
            Opcodes.GETFIELD -> {
                /// receiver, field
                super.visitInsn(Opcodes.SWAP) /// field, receiver
            }
            Opcodes.PUTFIELD -> {
                /// receiver, value, field
                super.visitInsn(if (isCategory2Type) Opcodes.DUP_X2 else Opcodes.DUP_X1) /// receiver, field, value, field
                super.visitInsn(Opcodes.POP) /// receiver, field, value
                super.visitInsn(if (isCategory2Type) Opcodes.DUP2_X2 else Opcodes.DUP_X2) /// value, receiver, field, value
                super.visitInsn(if (isCategory2Type) Opcodes.POP2 else Opcodes.POP) /// value, receiver, field
                super.visitInsn(Opcodes.SWAP) /// value, field, receiver
                super.visitInsn(if (isCategory2Type) Opcodes.DUP2_X2 else Opcodes.DUP2_X1) /// field, receiver, value, field, receiver
                super.visitInsn(Opcodes.POP2) /// field, receiver, value
            }
            else -> throw IllegalStateException("Unexpected opcode $opcode")
        }

        val isPut = opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD
        if (isPut && valueType != boxedValueType) {
            boxValue(valueType, boxedValueType)
        }

        val methodName = if (isPut) "set" else "get"
        val methodSignature = if (isPut) "(Ljava/lang/Object;Ljava/lang/Object;)V" else "(Ljava/lang/Object;)Ljava/lang/Object;"
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", methodName, methodSignature, false)

        if (!isPut) {
            super.visitTypeInsn(Opcodes.CHECKCAST, boxedValueType.internalName)

            if (valueType != boxedValueType) {
                unboxValue(valueType, boxedValueType)
            }
        }
    }

    private fun findMethod(owner: String, name: String, descriptor: String): PsiMethod? {
        val declaringClass = findClass(owner) ?: return null
        val methods = if (name == "<init>") declaringClass.constructors else declaringClass.findMethodsByName(name, true)

        for (method in methods) {
            val methodDescriptor = ClassUtil.getAsmMethodSignature(method)
            if (methodDescriptor == descriptor) {
                return method
            }
        }

        return null
    }

    private class ResolvedMethodCall(
        val declaringClass: PsiClass,
        val declaringClassType: Type,
        val parameterTypes: List<Type>,
        val boxedParameterTypes: List<Type>
    )

    private fun resolveApplicableMethodCall(opcode: Int, owner: String, name: String, descriptor: String): ResolvedMethodCall? {
        if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKESPECIAL) {
            return null
        }

        if (name.startsWith("<") && name != "<init>") {
            return null
        } else if (name == "<init>") {
            // Constructors are filtered out here as they need to be properly supported
            // (for instance, we need to deal with the preceding NEW instruction somehow).
            return null
        }

        val method = findMethod(owner, name, descriptor)
        val declaringClass = method?.containingClass
        if (method == null || method.hasModifierProperty(PsiModifier.PUBLIC) || declaringClass == null) {
            return null
        }

        val declaringClassType = PsiElementFactory.getInstance(project).createType(declaringClass)
        val declaringClassTypeDescriptor = ClassUtil.getBinaryPresentation(declaringClassType).takeIf { it.isNotEmpty() } ?: return null
        val declaringClassAsmType = Type.getType(declaringClassTypeDescriptor)

        val parameterCount = method.parameterList.parametersCount
        val parameterTypes = ArrayList<Type>(parameterCount)
        val boxedParameterTypes = ArrayList<Type>(parameterCount)

        for (index in 0 until parameterCount) {
            val parameter = method.parameterList.getParameter(index) ?: return null
            val parameterTypeDescriptor = ClassUtil.getBinaryPresentation(parameter.type).takeIf { it.isNotEmpty() } ?: return null
            val parameterType = Type.getType(parameterTypeDescriptor)
            parameterTypes += parameterType
            boxedParameterTypes += box(parameterType)
        }

        return ResolvedMethodCall(declaringClass, declaringClassAsmType, parameterTypes, boxedParameterTypes)
    }

    private fun fetchReflectionMethod(name: String, resolvedCall: ResolvedMethodCall) {
        val isConstructor = name == "<init>"

        pushClassLiteral(resolvedCall.declaringClassType, resolvedCall.declaringClass)

        if (!isConstructor) {
            super.visitLdcInsn(name)
        }

        // Put argument types to an array
        run {
            super.visitLdcInsn(resolvedCall.parameterTypes.size)
            super.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class") /// ..., types

            for (index in 0 until resolvedCall.parameterTypes.size) {
                val parameterType = resolvedCall.parameterTypes[index]
                val boxedParameterType = resolvedCall.boxedParameterTypes[index]

                super.visitInsn(Opcodes.DUP) /// ..., types, types
                super.visitLdcInsn(index) /// ..., types, types, index
                if (parameterType != boxedParameterType) {
                    super.visitFieldInsn(Opcodes.GETSTATIC, boxedParameterType.internalName, "TYPE", "Ljava/lang/Class;")
                } else {
                    pushClassLiteral(parameterType)
                }
                /// ..., types, types, index, type
                super.visitInsn(Opcodes.AASTORE) /// ..., types
            }
        }

        val getMethodName = if (isConstructor) "getDeclaredConstructor" else "getDeclaredMethod"
        val getMethodSignature =
            if (isConstructor) "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"
            else "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"

        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", getMethodName, getMethodSignature, false) /// ..., method
        super.visitInsn(Opcodes.DUP) /// ..., method, method
        super.visitLdcInsn(1) /// ..., method, method, 'true'
        super.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/reflect/AccessibleObject",
            "setAccessible",
            "(Z)V",
            false
        ) /// ..., method
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        val resolvedCall = resolveApplicableMethodCall(opcode, owner, name, descriptor)
        if (resolvedCall == null) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            return
        }

        val isConstructor = name == "<init>"
        val isStatic = opcode == Opcodes.INVOKESTATIC
        val parameterCount = resolvedCall.parameterTypes.size

        super.visitLdcInsn(parameterCount)
        super.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")

        // Put boxed arguments to an array
        run {
            for (index in (parameterCount - 1) downTo 0) {
                val parameterType = resolvedCall.parameterTypes[index]
                val boxedParameterType = resolvedCall.boxedParameterTypes[index]

                /// currentValue, args
                super.visitInsn(Opcodes.DUP) /// currentValue, args, args
                super.visitInsn(if (parameterType.size == 2) Opcodes.DUP2_X2 else Opcodes.DUP2_X1) /// args, args, currentValue, args, args
                super.visitInsn(Opcodes.POP2) /// args, args, currentValue

                if (parameterType != boxedParameterType) {
                    boxValue(parameterType, boxedParameterType)
                }

                super.visitLdcInsn(index) /// args, args, currentValue, index
                super.visitInsn(Opcodes.SWAP) /// args, args, index, currentValue
                super.visitInsn(Opcodes.AASTORE) /// args
            }
        }

        if (isStatic) {
            /// args
            super.visitInsn(Opcodes.ACONST_NULL) /// args, receiver
            super.visitInsn(Opcodes.SWAP) /// receiver, args
        }

        // Get accessible Method/Constructor instance
        fetchReflectionMethod(name, resolvedCall)

        if (isConstructor) {
            super.visitInsn(Opcodes.SWAP) /// method, args
        } else {
            super.visitInsn(Opcodes.DUP_X2) /// method, receiver, args, method
            super.visitInsn(Opcodes.POP) /// method, receiver, args
        }

        val invokeReceiverType = if (isConstructor) "java/lang/reflect/Constructor" else "java/lang/reflect/Method"
        val invokeMethodName = if (isConstructor) "newInstance" else "invoke"
        val invokeMethodSignature =
            if (isConstructor) "([Ljava/lang/Object;)Ljava/lang/Object;"
            else "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"

        /// method, [receiver], args
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, invokeReceiverType, invokeMethodName, invokeMethodSignature, false)

        val returnType = if (isConstructor) resolvedCall.declaringClassType else Type.getMethodType(descriptor).returnType
        val boxedReturnType = box(returnType)

        super.visitTypeInsn(Opcodes.CHECKCAST, boxedReturnType.internalName)
        if (returnType != boxedReturnType) {
            unboxValue(returnType, boxedReturnType)
        }
    }
}