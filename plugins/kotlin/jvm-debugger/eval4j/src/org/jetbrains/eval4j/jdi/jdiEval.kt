// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.eval4j.jdi

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.openapi.util.text.StringUtil
import com.sun.jdi.*
import org.jetbrains.eval4j.*
import org.jetbrains.eval4j.Value
import org.jetbrains.org.objectweb.asm.Type
import java.lang.reflect.AccessibleObject
import com.sun.jdi.Type as jdi_Type
import com.sun.jdi.Value as jdi_Value

private val CLASS_TYPE: Type = Type.getType(Class::class.java)
private val OBJECT_TYPE: Type = Type.getType(Any::class.java)
internal val STRING_TYPE: Type = Type.getType(String::class.java)
private val BOOTSTRAP_CLASS_DESCRIPTORS = setOf(STRING_TYPE.descriptor, Type.getDescriptor(ClassLoader::class.java), CLASS_TYPE.descriptor)

open class JDIEval(
    private val vm: VirtualMachine,
    private val defaultClassLoader: ClassLoaderReference?,
    protected val thread: ThreadReference,
    private val invokePolicy: Int
) : Eval {

    private val primitiveTypes = mapOf(
        Type.BOOLEAN_TYPE.className to vm.mirrorOf(true).type(),
        Type.BYTE_TYPE.className to vm.mirrorOf(1.toByte()).type(),
        Type.SHORT_TYPE.className to vm.mirrorOf(1.toShort()).type(),
        Type.INT_TYPE.className to vm.mirrorOf(1).type(),
        Type.CHAR_TYPE.className to vm.mirrorOf('1').type(),
        Type.LONG_TYPE.className to vm.mirrorOf(1L).type(),
        Type.FLOAT_TYPE.className to vm.mirrorOf(1.0f).type(),
        Type.DOUBLE_TYPE.className to vm.mirrorOf(1.0).type()
    )

    private val isJava8OrLater = StringUtil.compareVersionNumbers(vm.version(), "1.8") >= 0

    override fun loadClass(classType: Type): Value {
        return loadType(classType, defaultClassLoader).classObject().asValue()
    }

    open fun loadType(classType: Type, classLoader: ClassLoaderReference?): ReferenceType {
        val loadedClasses = vm.classesByName(classType.className)
        if (loadedClasses.isNotEmpty()) {
            for (loadedClass in loadedClasses) {
                if (loadedClass.isPrepared && (classType.descriptor in BOOTSTRAP_CLASS_DESCRIPTORS || loadedClass.classLoader() == classLoader)) {
                    return loadedClass
                }
            }
        }
        if (classLoader == null) {
            return invokeStaticMethod(
                MethodDescription(
                    CLASS_TYPE.internalName,
                    "forName",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    true
                ),
                listOf(loadString(classType.jdiName))
            ).jdiClass!!.reflectedType()
        } else {
            return invokeStaticMethod(
                MethodDescription(
                    CLASS_TYPE.internalName,
                    "forName",
                    "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;",
                    true
                ),
                listOf(
                    loadString(classType.jdiName),
                    boolean(true),
                    classLoader.asValue()
                )
            ).jdiClass!!.reflectedType()
        }
    }


    private fun loadClassByName(name: String, classLoader: ClassLoaderReference): jdi_Type {
        val dimensions = name.count { it == '[' }
        val baseTypeName = if (dimensions > 0) name.substring(0, name.indexOf('[')) else name

        val baseType = primitiveTypes[baseTypeName] ?: Type.getType("L$baseTypeName;").asReferenceType(classLoader)

        return if (dimensions == 0)
            baseType
        else
            Type.getType("[".repeat(dimensions) + baseType.asType().descriptor).asReferenceType(classLoader)
    }

    open fun jdiMirrorOfString(str: String): StringReference = vm.mirrorOf(str)

    override fun loadString(str: String): Value = jdiMirrorOfString(str).asValue()

    override fun newInstance(classType: Type): Value {
        return NewObjectValue(classType)
    }

    override fun isInstanceOf(value: Value, targetType: Type): Boolean {
        assert(targetType.sort == Type.OBJECT || targetType.sort == Type.ARRAY) {
            "Can't check isInstanceOf() for non-object type $targetType"
        }

        val jdiValue = value.asJdiValue(vm) { OBJECT_TYPE } ?: return false
        return DebuggerUtils.instanceOf(jdiValue.type(), targetType.className)
    }

    private fun Type.asReferenceType(classLoader: ClassLoaderReference? = this@JDIEval.defaultClassLoader): ReferenceType =
        loadType(this, classLoader)

    private fun Type.asArrayType(classLoader: ClassLoaderReference? = this@JDIEval.defaultClassLoader): ArrayType =
        asReferenceType(classLoader) as ArrayType

    open fun jdiNewArray(arrayType: ArrayType, size: Int): ArrayReference = arrayType.newInstance(size)

    override fun newArray(arrayType: Type, size: Int): Value {
        val jdiArrayType = arrayType.asArrayType()
        return jdiNewArray(jdiArrayType, size).asValue()
    }

    private val Type.arrayElementType: Type
        get(): Type {
            assert(sort == Type.ARRAY) { "Not an array type: $this" }
            return Type.getType(descriptor.substring(1))
        }

    private fun fillArray(elementType: Type, size: Int, nestedSizes: List<Int>): Value {
        val arr = newArray(Type.getType("[" + elementType.descriptor), size)
        if (nestedSizes.isNotEmpty()) {
            val nestedElementType = elementType.arrayElementType
            val nestedSize = nestedSizes[0]
            val tail = nestedSizes.drop(1)
            for (i in 0 until size) {
                setArrayElement(arr, int(i), fillArray(nestedElementType, nestedSize, tail))
            }
        }
        return arr
    }

    override fun newMultiDimensionalArray(arrayType: Type, dimensionSizes: List<Int>): Value {
        return fillArray(arrayType.arrayElementType, dimensionSizes[0], dimensionSizes.drop(1))
    }

    private fun Value.array() = jdiObj.checkNull() as ArrayReference

    override fun getArrayLength(array: Value): Value {
        return int(array.array().length())
    }

    override fun getArrayElement(array: Value, index: Value): Value {
        try {
            return array.array().getValue(index.int).asValue()
        } catch (e: IndexOutOfBoundsException) {
            throwInterpretingException(ArrayIndexOutOfBoundsException(e.message))
        }
    }

    override fun setArrayElement(array: Value, index: Value, newValue: Value) {
        try {
            return array.array().setValue(index.int, newValue.asJdiValue(vm) { array.asmType.arrayElementType })
        } catch (e: IndexOutOfBoundsException) {
            throwInterpretingException(ArrayIndexOutOfBoundsException(e.message))
        } catch (e: InvalidTypeException) {
            throwInterpretingException(ArrayStoreException(e.message))
        }
    }

    private fun findField(fieldDesc: FieldDescription, receiver: ReferenceType? = null): Field {
        if (receiver != null) {
            DebuggerUtils.findField(receiver, fieldDesc.name)?.let { return it }
        }
        DebuggerUtils.findField(fieldDesc.ownerType.asReferenceType(), fieldDesc.name)?.let { return it }

        throwBrokenCodeException(NoSuchFieldError("Field not found: $fieldDesc"))
    }

    private fun findStaticField(fieldDesc: FieldDescription): Field {
        val field = findField(fieldDesc)
        if (!field.isStatic) {
            throwBrokenCodeException(NoSuchFieldError("Field is not static: $fieldDesc"))
        }
        return field
    }

    override fun getStaticField(fieldDesc: FieldDescription): Value {
        val field = findStaticField(fieldDesc)
        return mayThrow { field.declaringType().getValue(field) }.ifFail(field).asValue()
    }

    override fun setStaticField(fieldDesc: FieldDescription, newValue: Value) {
        val field = findStaticField(fieldDesc)

        if (field.isFinal) {
            throwBrokenCodeException(NoSuchFieldError("Can't modify a final field: $field"))
        }

        val clazz = field.declaringType() as? ClassType
            ?: throwBrokenCodeException(NoSuchFieldError("Can't a field in a non-class: $field"))

        val jdiValue = newValue.asJdiValue(vm) { field.type().asType() }
        mayThrow { clazz.setValue(field, jdiValue) }.ifFail(field)
    }

    private fun findMethod(methodDesc: MethodDescription, clazz: ReferenceType = methodDesc.ownerType.asReferenceType()): Method {
        val method = findMethodOrNull(methodDesc, clazz)
        if (method != null) {
            return method
        }

        throwBrokenCodeException(NoSuchMethodError("Method not found: $methodDesc"))
    }

    private fun findMethodOrNull(methodDesc: MethodDescription, clazz: ReferenceType): Method? {
        val method = DebuggerUtils.findMethod(clazz, methodDesc.name, methodDesc.desc)
        if (method != null) {
            return method
        }

        // Module name can be different for internal functions during evaluation and compilation
        val internalNameWithoutSuffix = internalNameWithoutModuleSuffix(methodDesc.name)
        if (internalNameWithoutSuffix != null) {
            val internalMethods = clazz.visibleMethods().filter {
                val name = it.name()
                name.startsWith(internalNameWithoutSuffix) && '$' in name && it.signature() == methodDesc.desc
            }

            if (internalMethods.isNotEmpty()) {
                return internalMethods.singleOrNull()
                    ?: throwBrokenCodeException(IllegalArgumentException("Several internal methods found for $methodDesc"))
            }
        }

        return null
    }

    private fun internalNameWithoutModuleSuffix(name: String): String? {
        val indexOfDollar = name.indexOf('$')
        val demangledName = if (indexOfDollar >= 0) name.substring(0, indexOfDollar) else null
        return if (demangledName != null) "$demangledName$" else null
    }

    open fun jdiInvokeStaticMethod(type: ClassType, method: Method, args: List<jdi_Value?>, invokePolicy: Int): jdi_Value? {
        return type.invokeMethod(thread, method, args, invokePolicy)
    }

    open fun jdiInvokeStaticMethod(type: InterfaceType, method: Method, args: List<jdi_Value?>, invokePolicy: Int): jdi_Value? {
        return type.invokeMethod(thread, method, args, invokePolicy)
    }

    override fun invokeStaticMethod(methodDesc: MethodDescription, arguments: List<Value>): Value {
        val method = findMethod(methodDesc)
        if (!method.isStatic) {
            throwBrokenCodeException(NoSuchMethodError("Method is not static: $methodDesc"))
        }
        val declaringType = method.declaringType()
        val args = mapArguments(arguments, method.safeArgumentTypes())

        if (shouldInvokeMethodWithReflection(method, args)) {
            return invokeMethodWithReflection(declaringType.asType(), NULL_VALUE, args, methodDesc)
        }

        args.disableCollection()

        val result = mayThrow {
            when (declaringType) {
                is ClassType -> jdiInvokeStaticMethod(declaringType, method, args, invokePolicy)
                is InterfaceType -> {
                    if (!isJava8OrLater) {
                        val message = "Calling interface static methods is not supported in JVM ${vm.version()} ($method)"
                        throwBrokenCodeException(NoSuchMethodError(message))
                    }

                    jdiInvokeStaticMethod(declaringType, method, args, invokePolicy)
                }
                else -> {
                    val message = "Calling static methods is only supported for classes and interfaces ($method)"
                    throwBrokenCodeException(NoSuchMethodError(message))
                }
            }
        }.ifFail(method)

        args.enableCollection()
        return result.asValue()
    }

    override fun getField(instance: Value, fieldDesc: FieldDescription): Value {
        val receiver = instance.jdiObj.checkNull()
        val field = findField(fieldDesc, receiver.referenceType())

        return mayThrow {
            try {
                receiver.getValue(field)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Possibly incompatible types: " +
                            "field declaring type = ${field.declaringType()}, " +
                            "instance type = ${receiver.referenceType()}"
                )
            }
        }.ifFail(field, receiver).asValue()
    }

    override fun setField(instance: Value, fieldDesc: FieldDescription, newValue: Value) {
        val receiver = instance.jdiObj.checkNull()
        val field = findField(fieldDesc, receiver.referenceType())

        val jdiValue = newValue.asJdiValue(vm) { field.type().asType() }
        mayThrow { receiver.setValue(field, jdiValue) }
    }

    private fun unboxType(boxedValue: Value, type: Type): Value {
        val method = when (type) {
            Type.INT_TYPE -> MethodDescription("java/lang/Integer", "intValue", "()I", false)
            Type.BOOLEAN_TYPE -> MethodDescription("java/lang/Boolean", "booleanValue", "()Z", false)
            Type.CHAR_TYPE -> MethodDescription("java/lang/Character", "charValue", "()C", false)
            Type.SHORT_TYPE -> MethodDescription("java/lang/Character", "shortValue", "()S", false)
            Type.LONG_TYPE -> MethodDescription("java/lang/Long", "longValue", "()J", false)
            Type.BYTE_TYPE -> MethodDescription("java/lang/Byte", "byteValue", "()B", false)
            Type.FLOAT_TYPE -> MethodDescription("java/lang/Float", "floatValue", "()F", false)
            Type.DOUBLE_TYPE -> MethodDescription("java/lang/Double", "doubleValue", "()D", false)
            else -> throw UnsupportedOperationException("Couldn't unbox non primitive type ${type.internalName}")
        }
        return invokeMethod(boxedValue, method, listOf(), true)
    }

    open fun jdiInvokeMethod(obj: ObjectReference, method: Method, args: List<jdi_Value?>, policy: Int): jdi_Value? {
        return obj.invokeMethod(thread, method, args, policy)
    }

    open fun jdiNewInstance(clazz: ClassType, ctor: Method, args: List<jdi_Value?>, policy: Int): jdi_Value? {
        return clazz.newInstance(thread, ctor, args, policy)
    }

    override fun invokeMethod(instance: Value, methodDesc: MethodDescription, arguments: List<Value>, invokeSpecial: Boolean): Value {
        if (invokeSpecial && methodDesc.name == "<init>") {
            // Constructor call
            val ctor = findMethod(methodDesc)
            val clazz = (instance as NewObjectValue).asmType.asReferenceType() as ClassType
            val args = mapArguments(arguments, ctor.safeArgumentTypes())
            args.disableCollection()
            val result = mayThrow { jdiNewInstance(clazz, ctor, args, invokePolicy) }.ifFail(ctor)
            args.enableCollection()
            instance.value = result
            return result.asValue()
        }

        fun doInvokeMethod(obj: ObjectReference, method: Method, policy: Int): Value {
            val args = mapArguments(arguments, method.safeArgumentTypes())

            if (shouldInvokeMethodWithReflection(method, args)) {
                return invokeMethodWithReflection(instance.asmType, instance, args, methodDesc)
            }

            args.disableCollection()
            val result = mayThrow { jdiInvokeMethod(obj, method, args, policy) }.ifFail(method, obj)
            args.enableCollection()
            return result.asValue()
        }

        val obj = instance.jdiObj.checkNull()
        return if (invokeSpecial) {
            val method = findMethod(methodDesc)
            doInvokeMethod(obj, method, invokePolicy or ObjectReference.INVOKE_NONVIRTUAL)
        } else {
            val method = findMethod(methodDesc, obj.referenceType() ?: methodDesc.ownerType.asReferenceType())
            doInvokeMethod(obj, method, invokePolicy)
        }
    }

    open fun shouldInvokeMethodWithReflection(method: Method, args: List<com.sun.jdi.Value?>): Boolean {
        if (method.isVarArgs) {
            return false
        }

        val argumentTypes = try {
            method.argumentTypes()
        } catch (e: ClassNotLoadedException) {
            return false
        }

        return args.zip(argumentTypes).any { (arg, type) -> isArrayOfInterfaces(arg?.type(), type) }
    }

    private fun isArrayOfInterfaces(valueType: jdi_Type?, expectedType: jdi_Type?): Boolean {
        return (valueType as? ArrayType)?.componentType() is InterfaceType && (expectedType as? ArrayType)?.componentType() == OBJECT_TYPE.asReferenceType()
    }

    private fun invokeMethodWithReflection(ownerType: Type, instance: Value, args: List<jdi_Value?>, methodDesc: MethodDescription): Value {
        val methodToInvoke = invokeMethod(
            loadClass(ownerType),
            MethodDescription(
                CLASS_TYPE.internalName,
                "getDeclaredMethod",
                "(Ljava/lang/String;[L${CLASS_TYPE.internalName};)Ljava/lang/reflect/Method;",
                true
            ),
            listOf(loadString(methodDesc.name), *methodDesc.parameterTypes.map { loadClass(it) }.toTypedArray())
        )

        invokeMethod(
            methodToInvoke,
            MethodDescription(
                Type.getType(AccessibleObject::class.java).internalName,
                "setAccessible",
                "(Z)V",
                true
            ),
            listOf(vm.mirrorOf(true).asValue())
        )

        val invocationResult = invokeMethod(
            methodToInvoke,
            MethodDescription(
                methodToInvoke.asmType.internalName,
                "invoke",
                "(L${OBJECT_TYPE.internalName};[L${OBJECT_TYPE.internalName};)L${OBJECT_TYPE.internalName};",
                true
            ),
            listOf(instance, mirrorOfArgs(args))
        )

        return if (methodDesc.returnType.sort != Type.OBJECT &&
            methodDesc.returnType.sort != Type.ARRAY &&
            methodDesc.returnType.sort != Type.VOID
        )
            unboxType(invocationResult, methodDesc.returnType)
        else
            invocationResult
    }

    private fun mirrorOfArgs(args: List<jdi_Value?>): Value {
        val arrayObject = newArray(Type.getType("[" + OBJECT_TYPE.descriptor), args.size)

        args.forEachIndexed { index, value ->
            val indexValue = vm.mirrorOf(index).asValue()
            setArrayElement(arrayObject, indexValue, value.asValue())
        }

        return arrayObject
    }

    private fun List<jdi_Value?>.disableCollection() {
        forEach { (it as? ObjectReference)?.disableCollection() }
    }

    private fun List<jdi_Value?>.enableCollection() {
        forEach { (it as? ObjectReference)?.enableCollection() }
    }


    private fun mapArguments(arguments: List<Value>, expectedTypes: List<jdi_Type>): List<jdi_Value?> {
        return arguments.zip(expectedTypes).map { (arg, expectedType) ->
            arg.asJdiValue(vm) { expectedType.asType() }
        }
    }

    private fun Method.safeArgumentTypes(): List<jdi_Type> {
        try {
            return argumentTypes()
        } catch (e: ClassNotLoadedException) {
            return argumentTypeNames()!!.map { name ->
                val classLoader = declaringType()?.classLoader()
                if (classLoader != null) {
                    return@map loadClassByName(name, classLoader)
                }

                when (name) {
                    "void" -> virtualMachine().mirrorOfVoid().type()
                    "boolean" -> primitiveTypes.getValue(Type.BOOLEAN_TYPE.className)
                    "byte" -> primitiveTypes.getValue(Type.BYTE_TYPE.className)
                    "char" -> primitiveTypes.getValue(Type.CHAR_TYPE.className)
                    "short" -> primitiveTypes.getValue(Type.SHORT_TYPE.className)
                    "int" -> primitiveTypes.getValue(Type.INT_TYPE.className)
                    "long" -> primitiveTypes.getValue(Type.LONG_TYPE.className)
                    "float" -> primitiveTypes.getValue(Type.FLOAT_TYPE.className)
                    "double" -> primitiveTypes.getValue(Type.DOUBLE_TYPE.className)
                    else -> virtualMachine().classesByName(name).firstOrNull()
                        ?: throw IllegalStateException("Unknown class $name")
                }
            }
        }
    }
}

internal val Type.jdiName: String
    get() = internalName.replace('/', '.')

private sealed class JdiOperationResult<T> {
    class Fail<T>(val cause: Exception) : JdiOperationResult<T>()
    class OK<T>(val value: T) : JdiOperationResult<T>()
}

private fun <T> mayThrow(f: () -> T): JdiOperationResult<T> {
    return try {
        JdiOperationResult.OK(f())
    } catch (e: IllegalArgumentException) {
        JdiOperationResult.Fail(e)
    } catch (e: InvocationException) {
        throw ThrownFromEvaluatedCodeException(e.exception().asValue())
    }
}

private fun memberInfo(member: TypeComponent, thisObj: ObjectReference?): String {
    return "\nmember = $member\nobjectRef = $thisObj"
}

private fun <T> JdiOperationResult<T>.ifFail(member: TypeComponent, thisObj: ObjectReference? = null): T {
    return ifFail { memberInfo(member, thisObj) }
}

private fun <T> JdiOperationResult<T>.ifFail(lazyMessage: () -> String): T {
    return when (this) {
        is JdiOperationResult.OK -> this.value
        is JdiOperationResult.Fail -> {
            if (cause is IllegalArgumentException) {
                throwBrokenCodeException(IllegalArgumentException(lazyMessage(), this.cause))
            } else {
                throwBrokenCodeException(IllegalStateException(lazyMessage(), this.cause))
            }
        }
    }
}