package com.intellij.debugger.streams.core.trace.impl.handler.unified

import com.intellij.debugger.streams.core.trace.dsl.*
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.core.trace.impl.handler.type.ClassTypeImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.core.wrapper.CallArgument
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.impl.CallArgumentImpl
import com.intellij.debugger.streams.core.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.openapi.util.TextRange

open class DistinctByKeyHandler(callNumber: Int,
                                private val myCall: IntermediateStreamCall,
                                dsl: Dsl,
                                private val functionApplyName: String = "apply",
                                protected val keyExtractorPosition: Int = 0,
                                protected val myKeyType: GenericType = dsl.types.ANY,
                                protected val afterValueType: GenericType = dsl.types.ANY,
                                ) : HandlerBase.Intermediate(dsl)
{
  protected companion object {
    const val KEY_EXTRACTOR_VARIABLE_PREFIX: String = "keyExtractor"
    const val TRANSITIONS_ARRAY_NAME: String = "transitionsArray"
  }

  protected val myPeekHandler: PeekTraceHandler = PeekTraceHandler(callNumber, "distinct", myCall.typeBefore, myCall.typeAfter, dsl)
  protected val myKeyExtractor: CallArgument
  protected val myTypeAfter: GenericType = myCall.typeAfter
  protected val myExtractorVariable: Variable
  protected val myBeforeTimes: ListVariable = dsl.list(dsl.types.INT, myCall.name + callNumber + "BeforeTimes")
  protected val myBeforeValues: ListVariable = dsl.list(dsl.types.ANY, myCall.name + callNumber + "BeforeValues")
  protected val myKeys: ListVariable = dsl.list(myKeyType, myCall.name + callNumber + "Keys")
  protected val myTime2ValueAfter: MapVariable = dsl.linkedMap(dsl.types.INT, afterValueType, myCall.name + callNumber + "After")

  init {
    val arguments = myCall.arguments
    assert(arguments.isNotEmpty()) { "Key extractor is not specified" }
    myKeyExtractor = arguments[keyExtractorPosition]
    myExtractorVariable = dsl.variable(ClassTypeImpl(myKeyExtractor.type), KEY_EXTRACTOR_VARIABLE_PREFIX + callNumber)
  }

  override fun additionalVariablesDeclaration(): List<VariableDeclaration> {
    val extractor = dsl.declaration(myExtractorVariable, TextExpression(myKeyExtractor.text), false)
    val variables =
      mutableListOf(extractor, myBeforeTimes.defaultDeclaration(), myBeforeValues.defaultDeclaration(),
                    myTime2ValueAfter.defaultDeclaration(), myKeys.defaultDeclaration())
    variables.addAll(myPeekHandler.additionalVariablesDeclaration())

    return variables
  }

  override fun transformCall(call: IntermediateStreamCall): IntermediateStreamCall {
    val newKeyExtractor = dsl.lambda("x") {
      val key = dsl.variable(myKeyType, "key")
      declare(key, myExtractorVariable.call(functionApplyName, lambdaArg), false)
      statement { myBeforeTimes.add(dsl.currentTime()) }
      statement { myBeforeValues.add(lambdaArg) }
      statement { myKeys.add(key) }
      doReturn(key)
    }.toCode()

    val newArgs = call.arguments.toMutableList()
    if (keyExtractorPosition < newArgs.size) {
      newArgs[keyExtractorPosition] = CallArgumentImpl(myKeyExtractor.type, newKeyExtractor)
    } else {
      newArgs.add(keyExtractorPosition, CallArgumentImpl(myKeyExtractor.type, newKeyExtractor))
    }

    return call.updateArguments(newArgs)
  }

  protected open fun getMapArguments(): Array<Expression> = emptyArray()

  override fun prepareResult(): CodeBlock {
    val keys2TimesBefore = dsl.map(myKeyType, dsl.types.list(dsl.types.INT), "keys2Times", *getMapArguments())
    val transitions = dsl.map(dsl.types.INT, dsl.types.INT, "transitionsMap")
    val nullKeyList = dsl.list(dsl.types.INT, "nullKeyList")

    return dsl.block {
      add(myPeekHandler.prepareResult())
      declare(keys2TimesBefore.defaultDeclaration())
      declare(transitions.defaultDeclaration())
      declare(nullKeyList.defaultDeclaration())

      integerIteration(myKeys.size(), block@ this) {
        val key = declare(variable(myKeyType, "key"), myKeys.get(loopVariable), false)
        val lst = list(dsl.types.INT, "lst")
        declare(lst, true)
        ifBranch(key same nullExpression) {
          lst assign nullKeyList
        }.elseBranch {
          add(keys2TimesBefore.computeIfAbsent(dsl, key, newList(types.INT), lst))
        }
        statement { lst.add(myBeforeTimes.get(loopVariable)) }
      }

      forEachLoop(variable(types.INT, "afterTime"), myTime2ValueAfter.keys()) {
        val afterTime = loopVariable
        val valueAfter = declare(variable(types.ANY, "valueAfter"), myTime2ValueAfter.get(loopVariable), false)
        val key = declare(variable(myKeyType, "key"), myKeyType.defaultValue.expr, true)
        val found = declare(variable(types.BOOLEAN, "found"), "false".expr, true)
        integerIteration(myBeforeTimes.size(), forEachLoop@ this) {
          val equals = valueAfter equals myBeforeValues.get(loopVariable)
          ifBranch(equals and !transitions.contains(myBeforeTimes.get(loopVariable))) {
            key assign myKeys.get(loopVariable)
            found assign "true".expr
            statement { breakIteration() }
          }
        }

        ifBranch(found) {
          val key2TimesBeforeList = list(dsl.types.INT, "key2TimesBeforeList")
          declare(key2TimesBeforeList, true)
          ifBranch(key same nullExpression) {
            key2TimesBeforeList assign nullKeyList
          }.elseBranch {
            key2TimesBeforeList assign keys2TimesBefore.get(key)
          }
          forEachLoop(variable(types.INT, "beforeTime"), key2TimesBeforeList) {
            statement { transitions.set(loopVariable, afterTime) }
          }
        }
      }

      add(transitions.convertToArray(this, "transitionsArray"))
    }
  }


  override fun getResultExpression(): Expression =
    dsl.newArray(dsl.types.ANY, myPeekHandler.resultExpression, TextExpression(TRANSITIONS_ARRAY_NAME))

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()

  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val callsAfter = ArrayList(myPeekHandler.additionalCallsAfter())
    val lambda = dsl.lambda("x") {
      doReturn(myTime2ValueAfter.set(dsl.currentTime(), lambdaArg))
    }

    callsAfter.add(dsl.createPeekCall(myTypeAfter, lambda))
    return callsAfter
  }

  protected fun CodeContext.integerIteration(border: Expression, block: CodeBlock, init: ForLoopBody.() -> Unit) {
    block.forLoop(declaration(variable(types.INT, "i"), TextExpression("0"), true),
                  TextExpression("i < ${border.toCode()}"),
                  TextExpression("i = i + 1"), init)
  }

  protected fun IntermediateStreamCall.updateArguments(args: List<CallArgument>): IntermediateStreamCall =
    IntermediateStreamCallImpl(myCall.name, myCall.genericArguments, args, typeBefore, typeAfter, textRange)

  open class DistinctByCustomKey(callNumber: Int,
                                 call: IntermediateStreamCall,
                                 extractorType: String,
                                 extractorExpression: String,
                                 dsl: Dsl
  )
    : DistinctByKeyHandler(callNumber, call.transform(extractorType, extractorExpression), dsl) {

    private companion object {
      fun IntermediateStreamCall.transform(extractorType: String, extractorExpression: String): IntermediateStreamCall =
        IntermediateStreamCallImpl("distinct", genericArguments, listOf(CallArgumentImpl(extractorType, extractorExpression)), typeBefore, typeAfter,
                                   TextRange.EMPTY_RANGE)

    }
  }
}
