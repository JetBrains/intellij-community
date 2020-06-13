/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmMultifileClass
@file:JvmName("UastUtils")

package org.jetbrains.uast

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiMethodUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Get the topmost parent qualified expression for the call expression.
 *
 * Example 1:
 *  Code: variable.call(args)
 *  Call element: E = call(args)
 *  Qualified parent (return value): Q = [getQualifiedCallElement](E) = variable.call(args)
 *
 * Example 2:
 *  Code: call(args)
 *  Call element: E = call(args)
 *  Qualified parent (return value): Q = [getQualifiedCallElement](E) = call(args) (no qualifier)
 *
 *  @return containing qualified expression if the call is a child of the qualified expression, call element otherwise.
 */
fun UExpression.getQualifiedParentOrThis(): UExpression {
  fun findParent(current: UExpression?, previous: UExpression): UExpression? = when (current) {
    is UQualifiedReferenceExpression -> {
      if (current.selector == previous)
        findParent(current.uastParent as? UExpression, current) ?: current
      else
        previous
    }
    is UParenthesizedExpression -> findParent(current.expression, previous) ?: previous
    else -> null
  }

  return findParent(uastParent as? UExpression, this) ?: this
}


fun UExpression.asQualifiedPath(): List<String>? {
  if (this is USimpleNameReferenceExpression) {
    return listOf(this.identifier)
  }
  else if (this !is UQualifiedReferenceExpression) {
    return null
  }

  var error = false
  val list = mutableListOf<String>()
  fun addIdentifiers(expr: UQualifiedReferenceExpression) {
    val receiver = expr.receiver.unwrapParenthesis()
    val selector = expr.selector as? USimpleNameReferenceExpression ?: run { error = true; return }
    when (receiver) {
      is UQualifiedReferenceExpression -> addIdentifiers(receiver)
      is USimpleNameReferenceExpression -> list += receiver.identifier
      else -> {
        error = true
        return
      }
    }
    list += selector.identifier
  }

  addIdentifiers(this)
  return if (error) null else list
}

/**
 * Return the list of qualified expressions.
 *
 * Example:
 *   Code: obj.call(param).anotherCall(param2).getter
 *   Qualified chain: [obj, call(param), anotherCall(param2), getter]
 *
 * @return list of qualified expressions, or the empty list if the received expression is not a qualified expression.
 */
fun UExpression?.getQualifiedChain(): List<UExpression> {
  fun collect(expr: UQualifiedReferenceExpression, chains: MutableList<UExpression>) {
    val receiver = expr.receiver.unwrapParenthesis()
    if (receiver is UQualifiedReferenceExpression) {
      collect(receiver, chains)
    }
    else {
      chains += receiver
    }

    val selector = expr.selector.unwrapParenthesis()
    if (selector is UQualifiedReferenceExpression) {
      collect(selector, chains)
    }
    else {
      chains += selector
    }
  }

  if (this == null) return emptyList()
  val qualifiedExpression = this as? UQualifiedReferenceExpression ?: return listOf(this)
  val chains = mutableListOf<UExpression>()
  collect(qualifiedExpression, chains)
  return chains
}

/**
 * Return the outermost qualified expression.
 *
 * @return the outermost qualified expression,
 *  this element if the parent expression is not a qualified expression,
 *  or null if the element is not a qualified expression.
 *
 *  Example:
 *   Code: a.b.c(asd).g
 *   Call element: c(asd)
 *   Outermost qualified (return value): a.b.c(asd).g
 */
fun UExpression.getOutermostQualified(): UQualifiedReferenceExpression? {
  tailrec fun getOutermostQualified(current: UElement?, previous: UExpression): UQualifiedReferenceExpression? = when (current) {
    is UQualifiedReferenceExpression -> getOutermostQualified(current.uastParent, current)
    is UParenthesizedExpression -> getOutermostQualified(current.uastParent, previous)
    else -> if (previous is UQualifiedReferenceExpression) previous else null
  }

  return getOutermostQualified(this.uastParent, this)
}

/**
 * Checks if the received expression is a qualified chain of identifiers, and the trailing part of such chain is [fqName].
 *
 * @param fqName the chain part to check against. Sequence of identifiers, separated by dot ('.'). Example: "com.example".
 * @return true, if the received expression is a qualified chain of identifiers, and the trailing part of such chain is [fqName].
 */
fun UExpression.matchesQualified(fqName: String): Boolean {
  val identifiers = this.asQualifiedPath() ?: return false
  val passedIdentifiers = fqName.trim('.').split('.')
  return identifiers == passedIdentifiers
}

/**
 * Checks if the received expression is a qualified chain of identifiers, and the leading part of such chain is [fqName].
 *
 * @param fqName the chain part to check against. Sequence of identifiers, separated by dot ('.'). Example: "com.example".
 * @return true, if the received expression is a qualified chain of identifiers, and the leading part of such chain is [fqName].
 */
fun UExpression.startsWithQualified(fqName: String): Boolean {
  val identifiers = this.asQualifiedPath() ?: return false
  val passedIdentifiers = fqName.trim('.').split('.')
  if (identifiers.size < passedIdentifiers.size) return false
  passedIdentifiers.forEachIndexed { i, passedIdentifier ->
    if (passedIdentifier != identifiers[i]) return false
  }
  return true
}

/**
 * Checks if the received expression is a qualified chain of identifiers, and the trailing part of such chain is [fqName].
 *
 * @param fqName the chain part to check against. Sequence of identifiers, separated by dot ('.'). Example: "com.example".
 * @return true, if the received expression is a qualified chain of identifiers, and the trailing part of such chain is [fqName].
 */
fun UExpression.endsWithQualified(fqName: String): Boolean {
  val identifiers = this.asQualifiedPath()?.asReversed() ?: return false
  val passedIdentifiers = fqName.trim('.').split('.').asReversed()
  if (identifiers.size < passedIdentifiers.size) return false
  passedIdentifiers.forEachIndexed { i, passedIdentifier ->
    if (passedIdentifier != identifiers[i]) return false
  }
  return true
}

@JvmOverloads
fun UElement.asRecursiveLogString(render: (UElement) -> String = { it.asLogString() }): String {
  val stringBuilder = StringBuilder()
  val indent = "    "

  accept(object : UastVisitor {
    private var level = 0

    override fun visitElement(node: UElement): Boolean {
      stringBuilder.append(indent.repeat(level))
      stringBuilder.append(render(node))
      stringBuilder.append('\n')
      level++
      return false
    }

    override fun afterVisitElement(node: UElement) {
      super.afterVisitElement(node)
      level--
    }
  })
  return stringBuilder.toString()
}

/**
 * @return method's containing class if the given method is main method,
 * or companion object's containing class if the given method is main method annotated with [kotlin.jvm.JvmStatic] in companion object,
 * otherwise *null*.
 */
fun getMainMethodClass(uMainMethod: UMethod): PsiClass? {
  if ("main" != uMainMethod.name) return null
  val containingClass = uMainMethod.uastParent as? UClass ?: return null
  val mainMethod = uMainMethod.javaPsi
  if (PsiMethodUtil.isMainMethod(mainMethod)) return containingClass.javaPsi

  //a workaround for KT-33956
  if (isKotlinParameterlessMain(mainMethod)) return containingClass.javaPsi

  // Check for @JvmStatic main method in companion object
  val parentClassForCompanionObject = (containingClass.uastParent as? UClass)?.javaPsi ?: return null

  val mainInClass = PsiMethodUtil.findMainInClass(parentClassForCompanionObject)

  if (mainMethod.manager.areElementsEquivalent(mainMethod, mainInClass)) {
    return parentClassForCompanionObject
  }

  return null
}

private fun isKotlinParameterlessMain(mainMethod: PsiMethod) =
  mainMethod.language.id == "kotlin"
  && mainMethod.parameterList.parameters.isEmpty()
  && PsiType.VOID == mainMethod.returnType
  && mainMethod.hasModifierProperty(PsiModifier.STATIC)

@ApiStatus.Experimental
fun findMainInClass(uClass: UClass?): PsiMethod? {
  val javaPsi = uClass?.javaPsi ?: return null
  PsiMethodUtil.findMainInClass(javaPsi)?.let { return it }

  //a workaround for KT-33956
  javaPsi.methods.find(::isKotlinParameterlessMain)?.let { return it }
  return null
}