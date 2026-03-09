// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.google.common.collect.ImmutableMap
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
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
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.quickfix.PyAddDunderMethodQuickFix
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.inspections.quickfix.PyAddSpecifierToFormatQuickFix
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFStringFragment
import com.jetbrains.python.psi.PyFStringFragmentFormatPart
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLiteralExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySliceItem
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_COMPLEX_TYPES
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_DECIMAL_TYPES
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_FORMAT_MINI_LANGUAGE_TYPES
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_INT_TYPES
import com.jetbrains.python.psi.PyStringDunderUtil.isAllowedFormatOverride
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAnyOrUnknown
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.pyi.PyiUtil
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

      // return number of arguments or null if it can not be computed
      fun inspectArguments(rightExpression: PyExpression?, problemTarget: PsiElement): Int? {
        val SIMPLE_RHS_EXPRESSIONS = arrayOf(
          PyLiteralExpression::class.java,
          PySubscriptionExpression::class.java,
          PyBinaryExpression::class.java,
          PyConditionalExpression::class.java
        )
        val builtinCache = PyBuiltinCache.getInstance(problemTarget)
        val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)

        val s = myFormatSpec["1"]
        if (rightExpression is PySubscriptionExpression &&
            rightExpression.indexExpression is PySliceItem && s != null
        ) {
          val sliceItem = rightExpression.indexExpression as PySliceItem
          val type = myTypeEvalContext.getType(rightExpression.operand) ?: return null
          val stringType = PyBuiltinCache.getInstance(rightExpression).getStringType(LanguageLevel.forElement(rightExpression))
          val listType: PyType? = PyBuiltinCache.getInstance(rightExpression).listType

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
            for (member in rightType.asUnionSequence()) {
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
          return inspectCallExpression(rightExpression, resolveContext, myTypeEvalContext).takeIf { it >= 0 }
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
          val builtinCache = PyBuiltinCache.getInstance(rightExpression)
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
              registerProblem(problemTarget, PyPsiBundle.problemMessage("INSP.str.format.key.has.no.argument", key))
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

      fun registerProblem(problemTarget: PsiElement, message: ProblemMessage) {
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
          val builtinCache = PyBuiltinCache.getInstance(expr)
          val bytesType = builtinCache.getBytesType(LanguageLevel.forElement(expr))
          val actualType = context.getType(expr)
          return bytesType != null && actualType != null && match(bytesType, actualType, context)
        }
      }
    }

    private class NewStyleInspection(
      private val myText: String,
      private val myProblemTarget: PyElement,
      private val myVisitor: Visitor,
      private val myTypeEvalContext: TypeEvalContext,
    ) {
      constructor(
        myFormatExpression: PyStringLiteralExpression,
        myVisitor: Visitor,
        myTypeEvalContext: TypeEvalContext,
      ) : this(myFormatExpression.text, myFormatExpression, myVisitor, myTypeEvalContext)

      constructor(
        myFormatExpression: PyFStringFragmentFormatPart,
        myVisitor: Visitor,
        myTypeEvalContext: TypeEvalContext,
      ) : this(myFormatExpression.text, myFormatExpression, myVisitor, myTypeEvalContext)

      var isProblem: Boolean = false
        private set

      private val myFormatSpec = mutableMapOf<String, Set<String>>()

      fun inspect() {
        val parser = PyStringFormatParser(myText)
        val chunks = parser.parseNewStyle().filterIsInstance<NewStyleSubstitutionChunk>()
        for ((i, chunk) in chunks.withIndex()) {
          val prevChunk = if (i > 0) chunks[i - 1] else null

          if (prevChunk != null) {
            if (prevChunk.manualPosition != null && chunk.autoPosition != null) {
              registerProblem(myProblemTarget, PyPsiBundle.message("INSP.manual.to.auto.field.numbering"))
            }
            else if (prevChunk.autoPosition != null && chunk.manualPosition != null) {
              registerProblem(myProblemTarget, PyPsiBundle.message("INSP.auto.to.manual.field.numbering"))
            }
          }
          val mappingKey = inspectNewStyleChunkAndGetMappingKey(chunk)
          val anchor = myProblemTarget as? PyStringLiteralExpression
          if (!isProblem && anchor != null) {
            inspectArguments(chunk, mappingKey, anchor)
          }
        }
      }

      fun inspectNewStyleChunkAndGetMappingKey(chunk: NewStyleSubstitutionChunk): String {
        val supportedTypes = HashSet<String>()
        var hasTypeOptions = false

        val mappingKey = chunk.mappingKey ?: chunk.position.toString()

        // inspect options available only for numeric types
        if (chunk.hasSignOption() || chunk.useAlternateForm() || chunk.hasZeroPadding() || chunk.hasThousandsSeparator()) {
          // TODO: not using "KNOWN_COMPLEX_TYPES" because some of those won't be available at runtime and -> Any, need to check QName
          specifyTypes(supportedTypes, setOf("complex"))
          hasTypeOptions = true
        }

        if (chunk.precision != null) {
          // TODO: actually availableTypes doesn't reject int, because int is compatible with float and complex, need to check QName
          specifyTypes(supportedTypes, setOf(PyNames.TYPE_STR, PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX))
          hasTypeOptions = true
        }

        val conversionType = chunk.conversionType
        if (conversionType != Character.MIN_VALUE) {
          NEW_STYLE_FORMAT_CONVERSIONS[conversionType]?.let {
            specifyTypes(supportedTypes, it)
            hasTypeOptions = true
          } ?: registerProblem(myProblemTarget, PyPsiBundle.problemMessage("INSP.unsupported.format.character", conversionType))
        }

        if (supportedTypes.isNotEmpty()) {
          myFormatSpec[mappingKey] = supportedTypes
        }
        else if (hasTypeOptions) {
          registerProblem(myProblemTarget, PyPsiBundle.message("INSP.incompatible.options", mappingKey))
        }
        return mappingKey
      }

      fun inspectArguments(chunk: NewStyleSubstitutionChunk, mappingKey: String, anchor: PyStringLiteralExpression) {
        val target = PySubstitutionChunkReference(anchor, chunk).resolve()
        val hasElementIndex = chunk.mappingKeyElementIndex != null
        if (target == null) {
          val chunkMapping = chunk.mappingKey
          if (chunkMapping != null) {
            registerProblem(
              myProblemTarget,
              if (hasElementIndex) PyPsiBundle.problemMessage("INSP.too.few.args.for.fmt.string")
              else PyPsiBundle.problemMessage(
                "INSP.str.format.key.has.no.argument",
                chunkMapping
              )
            )
          }
          else {
            registerProblem(myProblemTarget, PyPsiBundle.message("INSP.too.few.args.for.fmt.string"))
          }
        }
        else {
          checkTypesCompatibleForCheckedTypesOnly(myProblemTarget, target, mappingKey)
        }
      }

      fun registerProblem(problemTarget: PsiElement, @InspectionMessage message: @InspectionMessage String) {
        isProblem = true
        myVisitor.registerProblem(problemTarget, message)
      }

      fun registerProblem(problemTarget: PsiElement, message: ProblemMessage) {
        isProblem = true
        myVisitor.registerProblem(problemTarget, message)
      }

      fun checkTypesCompatibleForCheckedTypesOnly(
        anchor: PyElement,
        target: PsiElement,
        mappingKey: String,
      ) {
        val typedElement = target as? PyTypedElement ?: return
        val type = myFormatSpec[mappingKey]?.joinToString(" or ") ?: return
        val actual = PyUnionType.toNonWeakType(myTypeEvalContext.getType(typedElement)) ?: return
        val expected = PyTypeParser.getTypeByName(anchor, type) ?: return
        if (
          actual.asUnionSequence().all { (it as? PyClassType)?.classQName in KNOWN_FORMAT_MINI_LANGUAGE_TYPES }
          && !match(expected, actual, myTypeEvalContext)
        ) {
          registerProblem(typedElement, PyPsiBundle.message("INSP.str.format.unexpected.argument.type", actual.name))
        }
      }

      companion object {

        private fun specifyTypes(types: MutableSet<String>, supportedTypes: Set<String>) {
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

    override fun visitPyFStringFragment(node: PyFStringFragment) {
      val formatPart = node.formatPart ?: return
      val expression = node.expression ?: return

      val formatSpecText = formatPart.text
      if (formatSpecText.isEmpty()) return
      val actualType = expression.getType(myTypeEvalContext)

      if (node.typeConversion == null) {
        val hasObjectFormat = actualType.asUnionSequence().firstOrNull {
          if (it.isAnyOrUnknown || it.hasWellKnownFormatMethod(expression, myTypeEvalContext)) false
          else (it as? PyClassType)?.pyClass?.let { pyClass ->
            // Type stubs (.pyi) frequently omit __str__, __repr__, and __format__ even when the runtime
            // .py module defines them, so fall back to the implementation class to avoid false positives.
            val implementation = PyiUtil.getOriginalElementOrLeaveAsIs(pyClass, PyClass::class.java)
            val implementationMethod = implementation.findMethodInImplementations(PyNames.DUNDER_FORMAT, myTypeEvalContext)
                                       ?: return@firstOrNull true
            implementationMethod.qualifiedName == "${PyNames.FQN.OBJECT}.${PyNames.DUNDER_FORMAT}"
          } ?: true
        }
        if (hasObjectFormat != null) {
          if (hasObjectFormat is PyClassType) {
            registerProblem(formatPart, PyPsiBundle.problemMessage("INSP.str.format.default.object.format", PyInspectionMessages.CodifiedParam.ofType(actualType, formatPart, myTypeEvalContext)),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            PyAddDunderMethodQuickFix(hasObjectFormat.pyClass, PyNames.DUNDER_FORMAT))
          }
          else registerProblem(formatPart, PyPsiBundle.problemMessage("INSP.str.format.default.object.format", PyInspectionMessages.CodifiedParam.ofType(actualType, formatPart, myTypeEvalContext)))
          return
        }
      }

      if (!actualType.asUnionSequence().any { it.hasWellKnownFormatMethod(expression, myTypeEvalContext) }) return

      val inspection = NewStyleInspection(formatPart, this, myTypeEvalContext)
      inspection.inspect()

      // If the format spec embeds nested expressions like `{width}`, we cannot statically know
      // what its final form looks like; skip the type-char and "invalid spec" checks.
      if (PsiTreeUtil.findChildOfType(formatPart, PyFStringFragment::class.java) != null) return

      val trimmedSpec = formatSpecText.trimEnd()
      val typeChar = trimmedSpec.lastOrNull() ?: return

      val expectedTypeNames = NEW_STYLE_FORMAT_CONVERSIONS[typeChar]
      if (expectedTypeNames != null) {
        checkFStringComponentType(node, expression, formatPart, typeChar, trimmedSpec.length - 1,
                                  allowedFormatOverrideQNames(typeChar))
        return
      }

      if (typeChar !in VALID_NON_TYPE_FORMAT_SPEC_ENDINGS) {
        val typeCharElement = formatPart.findElementAt(trimmedSpec.length - 1) ?: formatPart
        registerProblem(typeCharElement, PyPsiBundle.problemMessage("INSP.str.format.invalid.format.spec", typeChar))
        return
      }

      // No presentation type char. The only type-restricting parts left are the options that are valid
      // for numeric values only (sign, alternate form, zero padding, thousands separator).
      val (optionChar, optionOffset) = firstNumericOnlyOption(trimmedSpec) ?: return
      checkFStringComponentType(node, expression, formatPart, optionChar, optionOffset,
                                KNOWN_COMPLEX_TYPES)
    }

    /**
     * Reports [expression] when its type is incompatible with a format component, mirroring the logic used for both
     * presentation type chars and numeric-only options. [allowedQNames] are the fully qualified names of types whose
     * `__format__` override is trusted to accept the component (e.g. `decimal.Decimal`, numpy scalars).
     * The problem is highlighted at [reportOffset] within the format part and names [reportChar].
     */
    private fun checkFStringComponentType(
      node: PyFStringFragment,
      expression: PyExpression,
      formatPart: PyFStringFragmentFormatPart,
      reportChar: Char,
      reportOffset: Int,
      allowedQNames: Set<String>,
    ) {
      val actual = if (node.typeConversion != null) {
        PyTypeParser.getTypeByName(expression, PyNames.TYPE_STR, myTypeEvalContext)
      }
      else {
        PyUnionType.toNonWeakType(myTypeEvalContext.getType(expression))
      }
      if (actual == null) return

      val incompatibleTypes = actual.asUnionSequence()
        .filter { member ->
          member != null
          && (member.hasWellKnownFormatMethod(expression, myTypeEvalContext)
              || member.isNoneType)
          && !member.isAllowedFormatOverride(allowedQNames, myTypeEvalContext)
        }
        .toList()
      if (incompatibleTypes.isEmpty()) return

      val reportElement = formatPart.findElementAt(reportOffset) ?: formatPart
      val incompatibleTypeName =
        incompatibleTypes.joinToString(" | ") { PythonDocumentationProvider.getTypeName(PyLiteralType.upcastLiteralToClass(it), myTypeEvalContext) }
      registerProblem(reportElement, PyPsiBundle.problemMessage("INSP.str.format.code.not.supported", reportChar, incompatibleTypeName))
    }

    /**
     * Returns true when this type's formatting is one we model and can validate format codes against. That is the
     * case when it is a type we explicitly know (a builtin such as `str`/`int`/`float`/`complex`, or a known numeric
     * library type listed in [KNOWN_FORMAT_MINI_LANGUAGE_TYPES] such as `decimal.Decimal`, `fractions.Fraction`, numpy scalars), or when
     * it is a subclass that inherits `__format__` from one of those well-known classes (e.g. `class MyInt(int)`).
     *
     * It returns false for a type that supplies its own `__format__` not backed by a well-known class (a user class,
     * or a subclass that redefines `__format__`): we cannot statically know which format specs the override accepts,
     * so we trust it. It also returns false for a type that merely inherits `object.__format__`, which accepts only an
     * empty spec — a separate concern from format-code mismatches.
     */
    private fun PyType?.hasWellKnownFormatMethod(location: PyExpression, context: TypeEvalContext): Boolean {
      if (this == null) return false
      if (this !is PyClassType) return false
      if (pyClass.qualifiedName in KNOWN_FORMAT_MINI_LANGUAGE_TYPES) return true
      val resolveContext = PyResolveContext.defaultContext(context)
      val results = this.resolveMember(PyNames.DUNDER_FORMAT, location, AccessDirection.READ, resolveContext)
                    ?: return false
      return results
        .mapNotNull { it.element as? PyFunction }
        .mapNotNull { it.containingClass?.qualifiedName }
        .any { it in KNOWN_FORMAT_MINI_LANGUAGE_TYPES }
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
        !pyClass.isSubclass(PyNames.FQN.TUPLE, evalContext) -> 1
        this is PyTupleType -> elementCount
        else -> -1
      }
    }
  }
}

private val VALID_NON_TYPE_FORMAT_SPEC_ENDINGS = ('0'..'9').toSet() +
                                                 setOf('<', '>', '^', '=', '+', '-', ' ', '_', ',', '#', 'z')

private val NEW_STYLE_FORMAT_CONVERSIONS = mapOf(
  's' to setOf("str"),
  'b' to setOf("int"),
  'c' to setOf("int"),
  'd' to setOf("int"),
  'o' to setOf("int"),
  'x' to setOf("int"),
  'X' to setOf("int"),
  'n' to setOf("int", "float", "complex"),
  'e' to setOf("int", "float", "complex"),
  'E' to setOf("int", "float", "complex"),
  'f' to setOf("int", "float", "complex"),
  'F' to setOf("int", "float", "complex"),
  'g' to setOf("int", "float", "complex"),
  'G' to setOf("int", "float", "complex"),
  '%' to setOf("int", "float"),
)


/**
 * Fully qualified names of types whose `__format__` override is trusted to accept the given presentation [typeChar],
 * even though the type is not one of the resolvable builtins (e.g. `decimal.Decimal`, `fractions.Fraction`, numpy scalars).
 */
private fun allowedFormatOverrideQNames(typeChar: Char): Set<String> = when (typeChar) {
  'b', 'c', 'd', 'o', 'x', 'X' -> KNOWN_INT_TYPES
  'e', 'E', 'f', 'F', 'g', 'G', 'n' -> KNOWN_COMPLEX_TYPES
  '%' -> KNOWN_DECIMAL_TYPES
  's' -> setOf(PyNames.FQN.STR)
  else -> emptySet()
}

private const val NEW_STYLE_ALIGN_SYMBOLS = "<>=^"
private const val NEW_STYLE_SIGN_SYMBOLS = "+- "

/**
 * Locates the first option in a trimmed, non-nested f-string format part that is valid for numeric values only:
 * a sign (`+`, `-`, space), the alternate form `#`, zero padding `0`, or the thousands separator `,`.
 * [spec] is the format part text, which starts with the `:` separator. After skipping it, the spec is matched against
 * the standard mini-language order (fill/align, sign, alternate form, zero padding, width, grouping, precision, type),
 * so that, e.g., a leading `0` is recognized as zero padding while `0` inside a width is not, and a `+` used as a fill
 * character is ignored.
 * Returns the option character together with its offset within [spec], or `null` if there is no such option.
 */
private fun firstNumericOnlyOption(spec: String): Pair<Char, Int>? {
  // Skip the leading ':' separator that the format part text always starts with.
  var i = if (spec.startsWith(':')) 1 else 0
  // [fill]align
  if (i + 1 < spec.length && spec[i + 1] in NEW_STYLE_ALIGN_SYMBOLS) i += 2
  else if (i < spec.length && spec[i] in NEW_STYLE_ALIGN_SYMBOLS) i += 1
  if (i < spec.length && spec[i] in NEW_STYLE_SIGN_SYMBOLS) return spec[i] to i
  if (i < spec.length && spec[i] == '#') return '#' to i
  if (i < spec.length && spec[i] == '0') return '0' to i
  while (i < spec.length && spec[i].isDigit()) i++
  if (i < spec.length && spec[i] == ',') return ',' to i
  return null
}
