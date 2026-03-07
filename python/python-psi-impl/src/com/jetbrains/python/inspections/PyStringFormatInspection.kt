// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.google.common.collect.ImmutableMap
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyStringFormatParser
import com.jetbrains.python.PyStringFormatParser.NewStyleSubstitutionChunk
import com.jetbrains.python.PyStringFormatParser.PercentSubstitutionChunk
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference
import com.jetbrains.python.inspections.quickfix.PyAddSpecifierToFormatQuickFix
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyLiteralExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySliceItem
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import java.math.BigInteger
import java.util.function.ToIntFunction
import java.util.stream.Collectors
import kotlin.math.max

class PyStringFormatInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    private class Inspection(private val myVisitor: Visitor, private val myTypeEvalContext: TypeEvalContext) {
      private val myUsedMappingKeys: MutableMap<String, Boolean> = HashMap()
      private var myExpectedArguments = 0
      var isProblem: Boolean = false
        private set

      private val myFormatSpec: MutableMap<String?, String> = LinkedHashMap()

      // return number of arguments or -1 if it can not be computed
      fun inspectArguments(rightExpression: PyExpression?, problemTarget: PsiElement): Int? {
        val SIMPLE_RHS_EXPRESSIONS = arrayOf(
          PyLiteralExpression::class.java,
          PySubscriptionExpression::class.java,
          PyBinaryExpression::class.java,
          PyConditionalExpression::class.java
        )
        val builtinCache = getInstance(problemTarget)
        val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)

        val s = myFormatSpec["1"]
        if (rightExpression is PySubscriptionExpression &&
            rightExpression.indexExpression is PySliceItem && s != null
        ) {
          val sliceItem = rightExpression.indexExpression as PySliceItem
          val type = myTypeEvalContext.getType(rightExpression.operand) ?: return null
          val stringType = getInstance(rightExpression).getStringType(LanguageLevel.forElement(rightExpression))
          val listType: PyType? = getInstance(rightExpression).listType

          if (match(listType, type, myTypeEvalContext)
              || match(stringType, type, myTypeEvalContext)
          ) {
            checkTypeCompatible(
              problemTarget, builtinCache.strType,
              PyTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext)
            )
            return 1
          }
          val lower: PyExpression? = sliceItem.lowerBound
          val upper: PyExpression? = sliceItem.upperBound
          val stride: PyExpression? = sliceItem.stride
          if (upper is PyNumericLiteralExpression) {
            val lowerVal: BigInteger?
            if (lower is PyNumericLiteralExpression) {
              lowerVal = lower.bigIntegerValue
            }
            else {
              lowerVal = BigInteger.ZERO
            }
            val count = (upper.bigIntegerValue.subtract(lowerVal)).toInt()
            val strideVal = if (stride is PyNumericLiteralExpression) {
              stride.bigIntegerValue.toInt()
            }
            else 1
            val res = count / strideVal
            val residue = if (count % strideVal == 0) 0 else 1
            return res + residue
          }
          return null
        }
        else if (PsiTreeUtil.instanceOf(rightExpression, *SIMPLE_RHS_EXPRESSIONS)) {
          if (s != null) {
            val rightType = myTypeEvalContext.getType(rightExpression!!)
            for (member in rightType.toStream()) {
              if (member is PyTupleType) {
                matchEntireTupleTypes(problemTarget, member)
                return member.elementCount
              }
              else {
                checkExpressionType(rightExpression, s, problemTarget)
              }
            }
          }
          return 1
        }
        else if (rightExpression is PyReferenceExpression) {
          if (PyNames.DUNDER_DICT == rightExpression.name) return null

          val resolveResults =
            rightExpression.multiFollowAssignmentsChain(resolveContext)
          if (resolveResults.isEmpty()) {
            return null
          }

          val pyElement = PyUtil.filterTopPriorityResults(resolveResults)[0].element
          if (pyElement === rightExpression || pyElement !is PyExpression) {
            return null
          }
          if (pyElement is PyDictLiteralExpression) {
            return inspectDict(rightExpression, problemTarget, true)
          }
          return inspectArguments(pyElement, problemTarget)
        }
        else if (rightExpression is PyCallExpression) {
          val callee = rightExpression.callee
          if (callee != null && "dict" == callee.name) return 1
          return inspectCallExpression(rightExpression, resolveContext, myTypeEvalContext)
        }
        else if (rightExpression is PyParenthesizedExpression) {
          val rhs = rightExpression.containedExpression
          if (rhs != null) {
            return inspectArguments(rhs, problemTarget)
          }
        }
        else if (rightExpression is PyTupleExpression) {
          if (PsiTreeUtil.isAncestor(problemTarget, rightExpression, false)) {
            val expressions = rightExpression.elements
            var i = 1
            for (expression in expressions) {
              val formatSpec = myFormatSpec[i.toString()]
              if (formatSpec != null) {
                checkExpressionType(expression, formatSpec, expression)
              }
              ++i
            }
            return expressions.size
          }
          else {
            val tupleType = myTypeEvalContext.getType(rightExpression) as PyTupleType?
            if (tupleType == null) {
              return null
            }
            matchEntireTupleTypes(problemTarget, tupleType)
            return tupleType.elementCount
          }
        }
        else if (rightExpression is PyDictLiteralExpression) {
          return inspectDict(rightExpression, problemTarget, false)
        }
        else if (PsiTreeUtil.instanceOf(rightExpression, PySequenceExpression::class.java, PyComprehensionElement::class.java)) {
          if (s != null) {
            checkTypeCompatible(
              problemTarget, builtinCache.strType,
              PyTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext)
            )
            return 1
          }
        }
        return null
      }

      fun matchEntireTupleTypes(rightExpression: PsiElement, rightExpressionType: PyTupleType?) {
        val expectedElementTypes = myFormatSpec.values.map { name ->
          val builtinCache = getInstance(rightExpression)
          val expected = PyTypeParser.getTypeByName(rightExpression, name, myTypeEvalContext)
          if (expected === builtinCache.strType) null
          else expected
        }
        val expectedTupleType = PyTupleType.create(rightExpression, expectedElementTypes)
        checkTypeCompatible(rightExpression, rightExpressionType, expectedTupleType)
      }

      // inspects dict expressions. Finds key-value pairs from subscriptions if addSubscriptions is true.
      fun inspectDict(rightExpression: PyExpression, problemTarget: PsiElement, addSubscriptions: Boolean): Int {
        val additionalExpressions: Map<PyExpression, PyExpression>
        val pyElement = if (addSubscriptions) {
          additionalExpressions = addSubscriptions(
            rightExpression.containingFile,
            rightExpression.text
          )
          val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)
          (rightExpression as PyReferenceExpression).followAssignmentsChain(resolveContext).element
        }
        else {
          additionalExpressions = HashMap()
          rightExpression
        }
        if (pyElement == null) return 0
        val expressions = (pyElement as PyDictLiteralExpression).elements
        if (myUsedMappingKeys.isEmpty()) {
          if (myExpectedArguments > 0) {
            if (myExpectedArguments > 1 && myExpectedArguments == (expressions.size + additionalExpressions.size)) {
              // probably "%s %s" % {'a':1, 'b':2}, with names forgotten in template
              registerProblem(rightExpression, PyPsiBundle.message("INSP.format.requires.no.mapping"))
            }
            else {
              // "braces: %s" % {'foo':1} gives "braces: {'foo':1}", implicit str() kicks in
              return 1
            }
          }
          else {
            // "foo" % {whatever} is just "foo"
            return 0
          }
        }
        var referenceKeyNumber = 0
        for (expression in expressions) {
          val key = expression.key
          val value = expression.value
          when (key) {
            is PyStringLiteralExpression -> resolveMappingKey(problemTarget, key, value)
            is PyReferenceExpression -> referenceKeyNumber++
          }
        }
        for (expression in additionalExpressions.entries) {
          val key = expression.key
          val value = expression.value
          when (key) {
            is PyStringLiteralExpression -> resolveMappingKey(problemTarget, key, value)
            is PyReferenceExpression -> referenceKeyNumber++
          }
        }

        var unresolved = 0
        for (key in myUsedMappingKeys.keys) {
          if (!myUsedMappingKeys[key]!!) {
            unresolved++
            if (unresolved > referenceKeyNumber) {
              registerProblem(problemTarget, PyPsiBundle.message("INSP.str.format.key.has.no.argument", key))
              break
            }
          }
        }
        return expressions.size + additionalExpressions.size
      }

      fun resolveMappingKey(problemTarget: PsiElement, key: PyStringLiteralExpression, value: PyExpression?) {
        val name = key.stringValue
        if (myUsedMappingKeys[name] != null) {
          myUsedMappingKeys[name] = true
          if (value != null) {
            checkExpressionType(value, myFormatSpec[name]!!, problemTarget)
          }
        }
      }

      fun registerProblem(
        problemTarget: PsiElement,
        @InspectionMessage message: @InspectionMessage String,
        quickFix: LocalQuickFix,
      ) {
        isProblem = true
        myVisitor.registerProblem(problemTarget, message, quickFix)
      }

      fun registerProblem(problemTarget: PsiElement, @InspectionMessage message: @InspectionMessage String) {
        isProblem = true
        myVisitor.registerProblem(problemTarget, message)
      }

      fun checkExpressionType(
        expression: PyExpression,
        expectedTypeName: String,
        problemTarget: PsiElement,
      ) {
        val actual = myTypeEvalContext.getType(expression)
        val expected = PyTypeParser.getTypeByName(problemTarget, expectedTypeName, myTypeEvalContext)
        if (actual != null) {
          checkTypeCompatible(problemTarget, actual, expected)
        }
      }

      fun checkTypeCompatible(
        problemTarget: PsiElement,
        actual: PyType?,
        expected: PyType?,
      ) {
        if (expected != null && PyNames.TYPE_STR == expected.name) {
          return
        }
        if (actual != null && !match(expected, actual, myTypeEvalContext)) {
          registerProblem(problemTarget, PyPsiBundle.message("INSP.str.format.unexpected.argument.type", actual.name))
        }
      }

      fun inspectPercentFormat(formatExpression: PyStringLiteralExpression) {
        val value = formatExpression.text
        val chunks = PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(value))

        myExpectedArguments = chunks.size
        myUsedMappingKeys.clear()

        // if use mapping keys
        val mapping = !chunks.isEmpty() && chunks[0]!!.mappingKey != null
        for (i in chunks.indices) {
          val chunk = chunks[i] as? PercentSubstitutionChunk
          if (chunk != null) {
            // Mapping key
            var mappingKey: String? = (i + 1).toString()
            if (mapping) {
              if (chunk.mappingKey == null || chunk.isUnclosedMapping) {
                registerProblem(formatExpression, PyPsiBundle.message("INSP.too.few.keys"))
                break
              }
              mappingKey = chunk.mappingKey
              myUsedMappingKeys[mappingKey!!] = false
            }

            // Minimum field width
            inspectWidth(formatExpression, chunk.width)

            // Precision
            inspectWidth(formatExpression, chunk.precision)

            // Format specifier
            val conversionType = chunk.conversionType
            if (conversionType == 'b') {
              val languageLevel = LanguageLevel.forElement(formatExpression)
              if (languageLevel.isOlderThan(LanguageLevel.PYTHON35) || !isBytesLiteral(formatExpression, myTypeEvalContext)) {
                registerProblem(formatExpression, PyPsiBundle.message("INSP.str.format.unsupported.format.character.b"))
                return
              }
            }
            val languageLevel = LanguageLevel.forElement(formatExpression)
            if (conversionType in PERCENT_FORMAT_CONVERSIONS && !(languageLevel.isPython2 && conversionType == 'a')) {
              myFormatSpec[mappingKey] = PERCENT_FORMAT_CONVERSIONS[conversionType]!!
              continue
            }
            registerProblem(
              formatExpression,
              PyPsiBundle.message("INSP.no.format.specifier.char"),
              PyAddSpecifierToFormatQuickFix()
            )
            return
          }
        }
      }


      fun inspectWidth(formatExpression: PyStringLiteralExpression, width: String?) {
        if ("*" != width) return
        ++myExpectedArguments
        if (!myUsedMappingKeys.isEmpty()) {
          registerProblem(
            formatExpression,
            PyPsiBundle.message("INSP.str.format.can.not.use.star.in.formats.when.using.mapping")
          )
        }
      }

      fun inspectValues(rightExpression: PyExpression?) {
        if (rightExpression == null) return
        if (rightExpression is PyParenthesizedExpression) {
          inspectValues(rightExpression.containedExpression)
        }
        else {
          val type = myTypeEvalContext.getType(rightExpression)
          if (type is PyClassType) {
            if (myUsedMappingKeys.isNotEmpty() && !PyABCUtil.isSubclass(type.pyClass, PyNames.MAPPING, myTypeEvalContext)) {
              registerProblem(rightExpression, PyPsiBundle.message("INSP.format.requires.mapping"))
              return
            }
          }
          inspectArgumentsNumber(rightExpression)
        }
      }

      fun inspectArgumentsNumber(rightExpression: PyExpression) {
        val arguments = inspectArguments(rightExpression, rightExpression)
        if (myUsedMappingKeys.isEmpty() && arguments != null) {
          if (myExpectedArguments < arguments) {
            registerProblem(rightExpression, PyPsiBundle.message("INSP.too.many.args.for.fmt.string"))
          }
          else if (myExpectedArguments > arguments) {
            registerProblem(rightExpression, PyPsiBundle.message("INSP.too.few.args.for.fmt.string"))
          }
        }
      }

      companion object {
        private val PERCENT_FORMAT_CONVERSIONS = ImmutableMap.builder<Char, String>()
          .put('d', "int or long or float")
          .put('i', "int or long or float")
          .put('o', "int or long or float")
          .put('u', "int or long or float")
          .put('x', "int or long or float")
          .put('X', "int or long or float")
          .put('e', "float")
          .put('E', "float")
          .put('f', "float")
          .put('F', "float")
          .put('g', "float")
          .put('G', "float")
          .put('c', "str")
          .put('r', "str")
          .put('a', "str")
          .put('s', "str")
          .put('b', "bytes")
          .build()

        private fun addSubscriptions(file: PsiFile?, operand: String?): Map<PyExpression, PyExpression> {
          val additionalExpressions: MutableMap<PyExpression, PyExpression> = HashMap()
          val subscriptionExpressions =
            PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression::class.java)
          for (expr in subscriptionExpressions) {
            if (expr.operand.text == operand) {
              val parent = expr.parent
              if (parent is PyAssignmentStatement) {
                if (expr == parent.leftHandSideExpression) {
                  val key = expr.indexExpression
                  if (key != null) {
                    additionalExpressions[key] = parent.assignedValue!!
                  }
                }
              }
            }
          }
          return additionalExpressions
        }

        private fun isBytesLiteral(expr: PyStringLiteralExpression, context: TypeEvalContext): Boolean {
          val builtinCache = getInstance(expr)
          val bytesType = builtinCache.getBytesType(LanguageLevel.forElement(expr))
          val actualType = context.getType(expr)
          return bytesType != null && actualType != null && match(bytesType, actualType, context)
        }
      }
    }

    private class NewStyleInspection(
      private val myFormatExpression: PyStringLiteralExpression,
      private val myVisitor: Visitor,
      private val myTypeEvalContext: TypeEvalContext,
    ) {
      var isProblem: Boolean = false
        private set

      private val myFormatSpec: MutableMap<String, String> = HashMap()

      fun inspect() {
        val value = myFormatExpression.text
        val parser = PyStringFormatParser(value)
        val chunks = parser.parseNewStyle().filterIsInstance<NewStyleSubstitutionChunk>()
        for ((i, chunk) in chunks.withIndex()) {
          val prevChunk = if (i > 0) chunks[i - 1] else null

          if (prevChunk != null) {
            if (prevChunk.manualPosition != null && chunk.autoPosition != null) {
              registerProblem(myFormatExpression, PyPsiBundle.message("INSP.manual.to.auto.field.numbering"))
            }
            else if (prevChunk.autoPosition != null && chunk.manualPosition != null) {
              registerProblem(myFormatExpression, PyPsiBundle.message("INSP.auto.to.manual.field.numbering"))
            }
          }
          val mappingKey = inspectNewStyleChunkAndGetMappingKey(chunk)
          if (!isProblem) {
            inspectArguments(chunk, mappingKey)
          }
        }
      }

      fun inspectNewStyleChunkAndGetMappingKey(chunk: NewStyleSubstitutionChunk): String {
        val supportedTypes = HashSet<String>()
        var hasTypeOptions = false

        val mappingKey = chunk.mappingKey ?: chunk.position.toString()

        // inspect options available only for numeric types
        if (chunk.hasSignOption() || chunk.useAlternateForm() || chunk.hasZeroPadding() || chunk.hasThousandsSeparator()) {
          specifyTypes(supportedTypes, NUMERIC_TYPES)
          hasTypeOptions = true
        }

        if (chunk.precision != null) {
          // TODO: actually availableTypes doesn't reject int, because int is compatible with float and complex
          val availableTypes = listOf("str", "float", "complex")
          specifyTypes(supportedTypes, availableTypes)
          hasTypeOptions = true
        }

        val conversionType = chunk.conversionType
        if (conversionType != Character.MIN_VALUE) {
          if (conversionType in NEW_STYLE_FORMAT_CONVERSIONS) {
            val s =
              NEW_STYLE_FORMAT_CONVERSIONS[conversionType]!!.split(" or ".toRegex()).dropLastWhile { it.isEmpty() }
            specifyTypes(supportedTypes, s)
            hasTypeOptions = true
          }
          else {
            registerProblem(myFormatExpression, PyPsiBundle.message("INSP.unsupported.format.character", conversionType))
          }
        }

        if (supportedTypes.isNotEmpty()) {
          myFormatSpec[mappingKey] = StringUtil.join(supportedTypes, " or ")
        }
        else if (hasTypeOptions) {
          registerProblem(myFormatExpression, PyPsiBundle.message("INSP.incompatible.options", mappingKey))
        }
        return mappingKey
      }

      fun inspectArguments(chunk: NewStyleSubstitutionChunk, mappingKey: String) {
        val target = PySubstitutionChunkReference(myFormatExpression, chunk).resolve()
        val hasElementIndex = chunk.mappingKeyElementIndex != null
        if (target == null) {
          val chunkMapping = chunk.mappingKey
          if (chunkMapping != null) {
            registerProblem(
              myFormatExpression,
              if (hasElementIndex) PyPsiBundle.message("INSP.too.few.args.for.fmt.string")
              else PyPsiBundle.message(
                "INSP.str.format.key.has.no.argument",
                chunkMapping
              )
            )
          }
          else {
            registerProblem(myFormatExpression, PyPsiBundle.message("INSP.too.few.args.for.fmt.string"))
          }
        }
        else {
          checkTypesCompatibleForCheckedTypesOnly(myFormatExpression, target, mappingKey)
        }
      }

      fun registerProblem(problemTarget: PsiElement, @InspectionMessage message: @InspectionMessage String) {
        isProblem = true
        myVisitor.registerProblem(problemTarget, message)
      }

      fun checkTypesCompatibleForCheckedTypesOnly(
        anchor: PyStringLiteralExpression,
        target: PsiElement,
        mappingKey: String,
      ) {
        val typedElement = PyUtil.`as`(target, PyTypedElement::class.java)
        if (typedElement != null && mappingKey in myFormatSpec) {
          val actual = PyUnionType.toNonWeakType(myTypeEvalContext.getType(typedElement))
          val expected = PyTypeParser.getTypeByName(anchor, myFormatSpec[mappingKey]!!)
          if (expected != null && actual != null && actual.toStream()
              .allMatch { member: PyType? -> member != null && member.name in CHECKED_TYPES }
              && !match(expected, actual, myTypeEvalContext)
          ) {
            registerProblem(typedElement, PyPsiBundle.message("INSP.str.format.unexpected.argument.type", actual.name))
          }
        }
      }

      companion object {
        private val CHECKED_TYPES = listOf(
          PyNames.TYPE_STR, PyNames.TYPE_INT, PyNames.TYPE_LONG, PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX, PyNames.NONE,
          "LiteralString"
        )

        private val NUMERIC_TYPES =
          listOf(PyNames.TYPE_INT, PyNames.TYPE_LONG, PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX)

        private val NEW_STYLE_FORMAT_CONVERSIONS = mapOf(
          's' to "str or None",
          'b' to "int",
          'c' to "int",
          'd' to "int",
          'o' to "int",
          'x' to "int",
          'X' to "int",
          'n' to "int or long or float or complex",
          'e' to "long or float or complex",
          'E' to "long or float or complex",
          'f' to "long or float or complex",
          'F' to "long or float or complex",
          'g' to "long or float or complex",
          'G' to "long or float or complex",
          '%' to "long or float"
        )

        private fun specifyTypes(types: MutableSet<String>, supportedTypes: List<String>) {
          if (types.isEmpty()) {
            types.addAll(supportedTypes)
          }
          else {
            types.retainAll(supportedTypes)
          }
        }
      }
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      if (node.leftExpression is PyStringLiteralExpression && node.isOperator("%")) {
        val inspection = Inspection(this, myTypeEvalContext)
        inspection.inspectPercentFormat(node.leftExpression as PyStringLiteralExpression)
        if (inspection.isProblem) {
          return
        }
        inspection.inspectValues(node.rightExpression)
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val callee = node.callee
      if (callee != null && callee.name != null && callee.name == PyNames.FORMAT) {
        val literalExpression =
          PsiTreeUtil.getChildOfType(callee, PyStringLiteralExpression::class.java)
        if (literalExpression != null) {
          val inspection = NewStyleInspection(literalExpression, this, myTypeEvalContext)
          inspection.inspect()
        }
      }
    }

    companion object {
      fun inspectCallExpression(
        callExpression: PyCallExpression,
        resolveContext: PyResolveContext,
        evalContext: TypeEvalContext,
      ): Int {
        val statistics = callExpression.multiResolveCalleeFunction(resolveContext)
          .stream()
          .map { it.getCallType(evalContext, callExpression) }
          .collect(
            Collectors.summarizingInt(
              ToIntFunction { callType ->
                when {
                  callType.isNoneType -> 1
                  callType is PyClassType -> {
                    callType.countElements(evalContext)
                  }
                  callType is PyUnionType -> {
                    var maxNumber = 1
                    var allForSure = true
                    for (member in callType.members) {
                      val classType = member as? PyClassType
                      if (classType != null) {
                        val elementsCount: Int = classType.countElements(evalContext)
                        allForSure = allForSure && elementsCount != -1
                        maxNumber = max(maxNumber, elementsCount)
                      }
                      else if (!member.isNoneType) {
                        allForSure = false
                      }
                    }
                    if (allForSure) maxNumber else -1
                  }
                  else -> -1
                }
              }
            )
          )

        return if (statistics.min == statistics.max) {
          statistics.min
        }
        else -1
      }

      private fun PyClassType.countElements(evalContext: TypeEvalContext) = when {
        !pyClass.isSubclass(PyNames.TUPLE, evalContext) -> 1
        this is PyTupleType -> elementCount
        else -> -1
      }
    }
  }
}
