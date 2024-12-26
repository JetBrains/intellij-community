// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.eval4j

import org.jetbrains.eval4j.ExceptionThrown.ExceptionKind
import org.jetbrains.eval4j.jdi.jdiName
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.util.Printer

interface InterpreterResult {
    override fun toString(): String
}

class ExceptionThrown(val exception: ObjectValue, val kind: ExceptionKind) : InterpreterResult {
    override fun toString(): String = "Thrown $exception: $kind"

    enum class ExceptionKind {
        FROM_EVALUATED_CODE,
        FROM_EVALUATOR,
        BROKEN_CODE
    }
}

data class ValueReturned(val result: Value) : InterpreterResult {
    override fun toString(): String = "Returned $result"
}

class AbnormalTermination(val message: String) : InterpreterResult {
    override fun toString(): String = "Terminated abnormally: $message"
}

interface InterpretationEventHandler {
    object NONE : InterpretationEventHandler {
        override fun instructionProcessed(insn: AbstractInsnNode): InterpreterResult? = null
        override fun exceptionThrown(currentState: Frame<Value>, currentInsn: AbstractInsnNode, exception: Value): InterpreterResult? = null
        override fun exceptionCaught(currentState: Frame<Value>, currentInsn: AbstractInsnNode, exception: Value): InterpreterResult? = null
    }

    // If a non-null value is returned, interpreter loop is terminated and that value is used as a result
    fun instructionProcessed(insn: AbstractInsnNode): InterpreterResult?

    fun exceptionThrown(currentState: Frame<Value>, currentInsn: AbstractInsnNode, exception: Value): InterpreterResult?
    fun exceptionCaught(currentState: Frame<Value>, currentInsn: AbstractInsnNode, exception: Value): InterpreterResult?
}

abstract class ThrownFromEvalExceptionBase(cause: Throwable) : RuntimeException(cause) {
    override fun toString(): String = "Thrown by evaluator: ${cause}"
}

class Eval4JIllegalArgumentException : IllegalArgumentException {
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(message: String) : super(message)
}

class Eval4JIllegalStateException(message: String, cause: Throwable) : IllegalStateException(message, cause)

class BrokenCode(cause: Throwable) : ThrownFromEvalExceptionBase(cause)

// Interpreting exceptions should not be sent to EA
// but, for now, report -- we want to analyze evaluation errors (they happen)
class Eval4JInterpretingException(override val cause: Throwable) : RuntimeException(cause)

class ThrownFromEvaluatedCodeException(val exception: ObjectValue) : RuntimeException() {
    override fun toString(): String = "Thrown from evaluated code: $exception"
}

fun interpreterLoop(
    m: MethodNode,
    initialState: Frame<Value>,
    eval: Eval,
    handler: InterpretationEventHandler = InterpretationEventHandler.NONE
): InterpreterResult {
    val firstInsn = m.instructions.first
    if (firstInsn == null) throw IllegalArgumentException("Empty method")

    var currentInsn = firstInsn

    fun goto(nextInsn: AbstractInsnNode?) {
        if (nextInsn == null) throw IllegalArgumentException("Instruction flow ended with no RETURN")
        currentInsn = nextInsn
    }

    val interpreter = SingleInstructionInterpreter(eval)
    val frame = Frame(initialState)
    val handlers = computeHandlers(m)

    class ResultException(val result: InterpreterResult) : RuntimeException()

    fun exceptionCaught(exceptionValue: Value, instanceOf: (Type) -> Boolean): Boolean {
        val catchBlocks = handlers[m.instructions.indexOf(currentInsn)] ?: listOf()
        for (catch in catchBlocks) {
            val exceptionTypeInternalName = catch.type
            if (exceptionTypeInternalName != null) {
                val exceptionType = Type.getObjectType(exceptionTypeInternalName)
                if (instanceOf(exceptionType)) {
                    val handled = handler.exceptionCaught(frame, currentInsn, exceptionValue)
                    if (handled != null) throw ResultException(handled)
                    frame.clearStack()
                    frame.push(exceptionValue)
                    goto(catch.handler)
                    return true
                }
            }
        }
        return false
    }

    fun exceptionCaught(exceptionValue: Value): Boolean = exceptionCaught(exceptionValue) { exceptionType ->
        eval.isInstanceOf(exceptionValue, exceptionType)
    }

    fun exceptionFromEvalCaught(exception: Throwable, exceptionValue: Value): Boolean {
        return exceptionCaught(exceptionValue) { exceptionType ->
            try {
                val exceptionClass = exception::class.java
                val clazz = Class.forName(
                    exceptionType.jdiName,
                    true,
                    exceptionClass.classLoader
                )
                clazz.isAssignableFrom(exceptionClass)
            } catch (e: ClassNotFoundException) {
                // If the class is not available in this VM, it can not be a superclass of an exception thrown in it
                false
            }
        }
    }

    try {
        loop@ while (true) {
            val insnOpcode = currentInsn.opcode

            when (currentInsn.type) {
                AbstractInsnNode.LABEL,
                AbstractInsnNode.FRAME,
                AbstractInsnNode.LINE -> {
                    // skip to the next instruction
                }

                else -> {
                    when (insnOpcode) {
                        GOTO -> {
                            goto((currentInsn as JumpInsnNode).label)
                            continue@loop
                        }

                        RET -> {
                            val varNode = currentInsn as VarInsnNode
                            val address = frame.getLocal(varNode.`var`)
                            goto((address as LabelValue).value)
                            continue@loop
                        }

                        // TODO: switch
                        LOOKUPSWITCH -> throw UnsupportedByteCodeException("LOOKUPSWITCH is not supported yet")
                        TABLESWITCH -> throw UnsupportedByteCodeException("TABLESWITCH is not supported yet")

                        IRETURN, LRETURN, FRETURN, DRETURN, ARETURN -> {
                            val value = frame.getStackTop()
                            val expectedType = Type.getReturnType(m.desc)
                            if (expectedType.sort == Type.OBJECT || expectedType.sort == Type.ARRAY) {
                                val coerced = if (value != NULL_VALUE && value.asmType != expectedType)
                                    ObjectValue(value.obj(), expectedType)
                                else value
                                return ValueReturned(coerced)
                            }
                            if (value.asmType != expectedType) {
                                assert(insnOpcode == IRETURN) { "Only ints should be coerced: ${Printer.OPCODES[insnOpcode]}" }

                                val coerced = when (expectedType.sort) {
                                    Type.BOOLEAN -> boolean(value.boolean)
                                    Type.BYTE -> byte(value.int.toByte())
                                    Type.SHORT -> short(value.int.toShort())
                                    Type.CHAR -> char(value.int.toChar())
                                    Type.INT -> int(value.int)
                                    else -> throw UnsupportedByteCodeException("Should not be coerced: $expectedType")
                                }
                                return ValueReturned(coerced)
                            }
                            return ValueReturned(value)
                        }
                        RETURN -> return ValueReturned(VOID_VALUE)
                        IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> {
                            if (interpreter.checkUnaryCondition(frame.getStackTop(), insnOpcode)) {
                                frame.execute(currentInsn, interpreter)
                                goto((currentInsn as JumpInsnNode).label)
                                continue@loop
                            }
                        }
                        IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> {
                            if (interpreter.checkBinaryCondition(frame.getStackTop(1), frame.getStackTop(0), insnOpcode)) {
                                frame.execute(currentInsn, interpreter)
                                goto((currentInsn as JumpInsnNode).label)
                                continue@loop
                            }
                        }

                        ATHROW -> {
                            val exceptionValue = frame.getStackTop() as ObjectValue
                            val handled = handler.exceptionThrown(frame, currentInsn, exceptionValue)
                            if (handled != null) return handled
                            if (exceptionCaught(exceptionValue)) continue@loop
                            return ExceptionThrown(exceptionValue, ExceptionKind.FROM_EVALUATED_CODE)
                        }

                        // Workaround for a bug in Kotlin: NoPatterMatched exception is thrown otherwise!
                        else -> {
                        }
                    }

                    try {
                        frame.execute(currentInsn, interpreter)
                    } catch (e: ThrownFromEvalExceptionBase) {
                        val exception = e.cause!!
                        val exceptionValue = ObjectValue(exception, Type.getType(exception::class.java))
                        val handled = handler.exceptionThrown(
                            frame, currentInsn,
                            exceptionValue
                        )
                        if (handled != null) return handled
                        if (exceptionFromEvalCaught(exception, exceptionValue)) continue@loop

                        val exceptionType = if (e is BrokenCode) ExceptionKind.BROKEN_CODE else ExceptionKind.FROM_EVALUATOR
                        return ExceptionThrown(exceptionValue, exceptionType)
                    } catch (e: ThrownFromEvaluatedCodeException) {
                        val handled = handler.exceptionThrown(frame, currentInsn, e.exception)
                        if (handled != null) return handled
                        if (exceptionCaught(e.exception)) continue@loop
                        return ExceptionThrown(e.exception, ExceptionKind.FROM_EVALUATED_CODE)
                    }
                }
            }

            val handled = handler.instructionProcessed(currentInsn)
            if (handled != null) return handled

            goto(currentInsn.next)
        }
    } catch (e: ResultException) {
        return e.result
    }
}

private fun <T : Value> Frame<T>.getStackTop(i: Int = 0) = this.getStack(this.stackSize - 1 - i) ?: throwBrokenCodeException(
    Eval4JIllegalArgumentException("Couldn't get value with index = $i from top of stack")
)

// Copied from org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer.analyze()
fun computeHandlers(m: MethodNode): Array<out List<TryCatchBlockNode>?> {
    val instructions = m.instructions
    val handlers = Array<MutableList<TryCatchBlockNode>?>(instructions.size()) { null }
    for (tcb in m.tryCatchBlocks) {
        val begin = instructions.indexOf(tcb.start)
        val end = instructions.indexOf(tcb.end)
        for (i in begin until end) {
            val insnHandlers = handlers[i] ?: ArrayList()
            handlers[i] = insnHandlers

            insnHandlers.add(tcb)
        }
    }

    return handlers
}