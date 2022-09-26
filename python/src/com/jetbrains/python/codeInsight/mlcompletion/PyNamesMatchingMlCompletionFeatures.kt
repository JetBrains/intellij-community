// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

object PyNamesMatchingMlCompletionFeatures {
  private val scopeNamesKey = Key<Map<String, Int>>("py.ml.completion.scope.names")
  private val scopeTokensKey = Key<Map<String, Int>>("py.ml.completion.scope.tokens")
  private val lineLeftNamesKey = Key<Map<String, Int>>("py.ml.completion.line.left.names")
  private val lineLeftTokensKey = Key<Map<String, Int>>("py.ml.completion.line.left.tokens")

  val importNamesKey = Key<Map<String, Int>>("py.ml.completion.import.names")
  private val importTokensKey = Key<Map<String, Int>>("py.ml.completion.import.tokens")

  val namedArgumentsNamesKey = Key<Map<String, Int>>("py.ml.completion.arguments.names")
  private val namedArgumentsTokensKey = Key<Map<String, Int>>("py.ml.completion.arguments.tokens")

  val statementListOrFileNamesKey = Key<Map<String, Int>>("py.ml.completion.statement.list.names")
  private val statementListOrFileTokensKey = Key<Map<String, Int>>("py.ml.completion.statement.list.tokens")

  private val enclosingMethodName = Key<String>("py.ml.completion.enclosing.method.name")

  data class PyScopeMatchingFeatures(val sumMatches: Int,
                                     val sumTokensMatches: Int,
                                     val numScopeNames: Int,
                                     val numScopeDifferentNames: Int)
  fun getPyFunClassFileBodyMatchingFeatures(contextFeatures: ContextFeatures, lookupString: String): PyScopeMatchingFeatures? {
    val names = contextFeatures.getUserData(scopeNamesKey) ?: return null
    val tokens = contextFeatures.getUserData(scopeTokensKey) ?: return null
    return getPyScopeMatchingFeatures(names, tokens, lookupString)
  }

  fun getPySameLineMatchingFeatures(contextFeatures: ContextFeatures, lookupString: String): PyScopeMatchingFeatures? {
    val names = contextFeatures.getUserData(lineLeftNamesKey) ?: return null
    val tokens = contextFeatures.getUserData(lineLeftTokensKey) ?: return null
    return getPyScopeMatchingFeatures(names, tokens, lookupString)
  }

  fun getNumTokensFeature(elementName: String) = getTokens(elementName).size

  data class MatchingWithReceiverFeatures(val matchesWithReceiver: Boolean,
                                          val receiverTokensNum: Int,
                                          val numMatchedTokens: Int)
  fun getMatchingWithReceiverFeatures(contextFeatures: ContextFeatures, element: LookupElement): MatchingWithReceiverFeatures? {
    val names = contextFeatures.getUserData(PyReceiverMlCompletionFeatures.receiverNamesKey) ?: return null
    if (names.isEmpty()) return null
    val matchesWithReceiver = names.any { it == element.lookupString }
    val maxMatchedToken = names.maxByOrNull { tokensMatched(element.lookupString, it) } ?: ""
    val numMatchedTokens = tokensMatched(maxMatchedToken, element.lookupString)
    val receiverTokensNum = getNumTokensFeature(maxMatchedToken)
    return MatchingWithReceiverFeatures(matchesWithReceiver, receiverTokensNum, numMatchedTokens)
  }

  fun getMatchingWithEnclosingMethodFeatures(contextFeatures: ContextFeatures, element: LookupElement): Map<String, MLFeatureValue> {
    val name = contextFeatures.getUserData(enclosingMethodName) ?: return emptyMap()
    val result = mutableMapOf<String, MLFeatureValue>()
    if (element.lookupString == name) result["matches_with_enclosing_method"] = MLFeatureValue.binary(true)
    result["matched_tokens_with_enclosing_method"] = MLFeatureValue.numerical(tokensMatched (name, element.lookupString))
    return result
  }

  fun calculateFunBodyNames(environment: CompletionEnvironment) {
    val position = environment.parameters.position
    val scope = PsiTreeUtil.getParentOfType(position, PyFile::class.java, PyFunction::class.java, PyClass::class.java)
    val names = collectUsedNames(scope)
    environment.putUserData(scopeNamesKey, names)
    environment.putUserData(scopeTokensKey, getTokensCounterMap(names).toMap())
  }

  fun calculateSameLineLeftNames(environment: CompletionEnvironment): Map<String, Int> {
    val position = environment.parameters.position
    var curElement = PsiTreeUtil.prevLeaf(position)
    val names = Counter<String>()
    while (curElement != null && !curElement.text.contains("\n")) {
      val text = curElement.text
      if (!StringUtil.isEmptyOrSpaces(text)) {
        names.add(text)
      }
      curElement = PsiTreeUtil.prevLeaf(curElement)
    }
    environment.putUserData(lineLeftNamesKey, names.toMap())
    environment.putUserData(lineLeftTokensKey, getTokensCounterMap(names.toMap()).toMap())
    return names.toMap()
  }

  fun calculateImportNames(environment: CompletionEnvironment) {
    environment.parameters.position.containingFile
      .let { PsiTreeUtil.collectElementsOfType(it, PyImportElement::class.java) }
      .mapNotNull { it.importReferenceExpression }
      .mapNotNull { it.name }
      .let { putTokensAndNamesToUserData(environment, importNamesKey, importTokensKey, it) }
  }

  fun calculateStatementListNames(environment: CompletionEnvironment) {
    val position = environment.parameters.position
    position
      .let { PsiTreeUtil.getParentOfType(it, PyStatementList::class.java, PyFile::class.java) }
      ?.let { PsiTreeUtil.collectElementsOfType(it, PyReferenceExpression::class.java) }
      ?.filter { it.textOffset < position.textOffset }
      ?.mapNotNull { it.name }
      ?.let { putTokensAndNamesToUserData(environment, statementListOrFileNamesKey, statementListOrFileTokensKey, it) }
  }

  fun calculateNamedArgumentsNames(environment: CompletionEnvironment) {
    val position = environment.parameters.position
    PsiTreeUtil.getParentOfType(position, PyArgumentList::class.java)
      ?.let { PsiTreeUtil.getChildrenOfType(it, PyKeywordArgument::class.java) }
      ?.mapNotNull { it.firstChild }
      ?.mapNotNull { it.text }
      ?.let { putTokensAndNamesToUserData(environment, namedArgumentsNamesKey, namedArgumentsTokensKey, it) }
  }

  fun calculateEnclosingMethodName(environment: CompletionEnvironment) {
    val position = environment.parameters.position
    val name = PsiTreeUtil.getParentOfType(position, PyFunction::class.java)?.name ?: return
    environment.putUserData(enclosingMethodName, name)
  }

  private fun putTokensAndNamesToUserData(environment: CompletionEnvironment,
                                          namesKey: Key<Map<String, Int>>,
                                          tokensKey: Key<Map<String, Int>>,
                                          names: List<String>) {
    names
      .groupingBy { it }
      .eachCount()
      .let { putTokensAndNamesToUserData(environment, namesKey, tokensKey, it) }
  }

  private fun putTokensAndNamesToUserData(environment: CompletionEnvironment,
                                          namesKey: Key<Map<String, Int>>,
                                          tokensKey: Key<Map<String, Int>>,
                                          names: Map<String, Int>) {
    environment.putUserData(namesKey, names.toMap())
    environment.putUserData(tokensKey, getTokensCounterMap(names.toMap()).toMap())
  }

  private fun getPyScopeMatchingFeatures(names: Map<String, Int>,
                                         tokens: Map<String, Int>,
                                         lookupString: String): PyScopeMatchingFeatures {
    val sumMatches = names[lookupString] ?: 0
    val sumTokensMatches = tokensMatched(lookupString, tokens)
    val total = names.toList().sumOf { it.second }
    return PyScopeMatchingFeatures(sumMatches, sumTokensMatches, total, names.size)
  }

  private fun collectUsedNames(scope: PsiElement?): Map<String, Int> {
    val variables = Counter<String>()

    if (scope !is PyClass && scope !is PyFile && scope !is PyFunction) {
      return variables.toMap()
    }

    val visitor = object : PyRecursiveElementVisitor() {
      override fun visitPyTargetExpression(node: PyTargetExpression) {
        variables.add(node.name)
      }

      override fun visitPyNamedParameter(node: PyNamedParameter) {
        variables.add(node.name)
      }

      override fun visitPyReferenceExpression(node: PyReferenceExpression) {
        if (!node.isQualified) {
          variables.add(node.referencedName)
        }
        else {
          super.visitPyReferenceExpression(node)
        }
      }

      override fun visitPyFunction(node: PyFunction) {
        variables.add(node.name)
      }

      override fun visitPyClass(node: PyClass) {
        variables.add(node.name)
      }
    }

    if (scope is PyFunction || scope is PyClass) {
      scope.accept(visitor)
      scope.acceptChildren(visitor)
    }
    else {
      scope.acceptChildren(visitor)
    }

    return variables.toMap().filter { !it.key.contains(DUMMY_IDENTIFIER_TRIMMED) }
  }

  private fun tokensMatched(firstName: String, secondName: String): Int {
    val nameTokens = getTokens(firstName)
    val elementNameTokens = getTokens(secondName)
    return nameTokens.sumOf { token1 -> elementNameTokens.count { token2 -> token1 == token2 } }
  }

  private fun tokensMatched(name: String, tokens: Map<String, Int>): Int {
    val nameTokens = getTokens(name)
    return nameTokens.sumOf { tokens[it] ?: 0 }
  }

  private fun getTokensCounterMap(names: Map<String, Int>): Counter<String> {
    val result = Counter<String>()
    names.forEach { (name, cnt) ->
      val tokens = getTokens(name)
      for (token in tokens) {
        result.add(token, cnt)
      }
    }
    return result
  }

  private fun getTokens(name: String): List<String> =
    name
      .split("_")
      .asSequence()
      .flatMap { splitByCamelCase(it).asSequence() }
      .filter { it.isNotEmpty() }
      .toList()

  private fun processToken(token: String): String {
    val lettersOnly = token.filter { it.isLetter() }
    return if (lettersOnly.length > 3) {
      when {
        lettersOnly.endsWith("s") -> lettersOnly.substring(0 until lettersOnly.length - 1)
        lettersOnly.endsWith("es") -> lettersOnly.substring(0 until lettersOnly.length - 2)
        else -> lettersOnly
      }
    }
    else lettersOnly
  }

  private fun splitByCamelCase(name: String): List<String> {
    if (isAllLettersUpper(name)) return arrayListOf(processToken(name.toLowerCase()))
    val result = ArrayList<String>()
    var curToken = ""
    for (ch in name) {
      if (ch.isUpperCase()) {
        if (curToken.isNotEmpty()) {
          result.add(processToken(curToken))
          curToken = ""
        }
        curToken += ch.toLowerCase()
      }
      else {
        curToken += ch
      }
    }
    if (curToken.isNotEmpty()) result.add(processToken(curToken))
    return result
  }

  private fun isAllLettersUpper(name: String) = !name.any { it.isLetter() && it.isLowerCase() }
}