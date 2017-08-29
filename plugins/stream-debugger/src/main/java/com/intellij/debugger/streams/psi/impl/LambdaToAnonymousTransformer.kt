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
package com.intellij.debugger.streams.psi.impl

import com.intellij.codeInsight.ChangeContextUtil
import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInsight.generation.OverrideImplementUtil.*
import com.intellij.codeInsight.generation.PsiGenerationInfo
import com.intellij.debugger.streams.psi.PsiElementTransformer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.*
import com.intellij.refactoring.util.RefactoringChangeUtil
import java.util.*

/**
 * Copied with minor changes from: LambdaCanBeReplacedWithAnonymousInspection
 * Cannot reuse because the call OverrideImplementUtil.overrideOrImplement require a VirtualFile
 *
 * @author Vitaliy.Bibaev
 */
object LambdaToAnonymousTransformer : PsiElementTransformer.Base() {
  val LOG = Logger.getInstance("#" + LambdaToAnonymousTransformer::class.java.name)

  override val visitor: PsiElementVisitor
    get() = object : JavaRecursiveElementVisitor() {
      override fun visitLambdaExpression(lambdaExpression: PsiLambdaExpression?) {
        if (lambdaExpression == null || !isConvertible(lambdaExpression)) return
        val paramListCopy = (lambdaExpression.parameterList.copy() as PsiParameterList).parameters
        val functionalInterfaceType = lambdaExpression.functionalInterfaceType ?: return
        val method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) ?: return

        val blockText = getBodyText(lambdaExpression) ?: return

        val project = lambdaExpression.project
        val psiElementFactory = JavaPsiFacade.getElementFactory(project)
        var blockFromText = psiElementFactory.createCodeBlockFromText(blockText, lambdaExpression)
        qualifyThisExpressions(lambdaExpression, psiElementFactory, blockFromText)
        blockFromText = psiElementFactory.createCodeBlockFromText(blockFromText.text, null)

        var newExpression = psiElementFactory.createExpressionFromText("new " + functionalInterfaceType.canonicalText + "(){}",
                                                                       lambdaExpression) as PsiNewExpression
        newExpression = lambdaExpression.replace(newExpression) as PsiNewExpression

        val anonymousClass = newExpression.anonymousClass
        LOG.assertTrue(anonymousClass != null)
        val infos = overrideOrImplement(anonymousClass!!, method)
        if (infos.size == 1) {
          val member = infos[0].psiMember
          val parameters = member.parameterList.parameters
          if (parameters.size == paramListCopy.size) {
            for (i in parameters.indices) {
              val parameter = parameters[i]
              val lambdaParamName = paramListCopy[i].name
              if (lambdaParamName != null) {
                parameter.setName(lambdaParamName)
              }
            }
          }
          val codeBlock = member.body
          LOG.assertTrue(codeBlock != null)
          codeBlock!!.replace(blockFromText)

          val parent = anonymousClass.parent.parent
          if (parent is PsiTypeCastExpression && RedundantCastUtil.isCastRedundant(parent)) {
            val operand = parent.operand
            LOG.assertTrue(operand != null)
            parent.replace(operand!!)
          }

          JavaCodeStyleManager.getInstance(project).qualifyClassReferences(anonymousClass)
        }
      }
    }

  private fun overrideOrImplement(psiClass: PsiAnonymousClass, baseMethod: PsiMethod): List<PsiGenerationInfo<PsiMethod>> {
    val prototypes = convert2GenerationInfos(overrideOrImplementMethod(psiClass, baseMethod, false))
    if (prototypes.isEmpty()) return emptyList()

    val substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseMethod.containingClass!!, psiClass, PsiSubstitutor.EMPTY)
    val anchor = getDefaultAnchorToOverrideOrImplement(psiClass, baseMethod, substitutor)
    return GenerateMembersUtil.insertMembersBeforeAnchor<PsiGenerationInfo<PsiMethod>>(psiClass, anchor, prototypes)
  }

  private fun getBodyText(lambda: PsiLambdaExpression): String? {
    var blockText: String?
    val body = lambda.body
    when {
      body is PsiExpression -> {
        val returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda)
        blockText = "{"
        blockText += if (PsiType.VOID == returnType) "" else "return "
        blockText += body.text + ";}"
      }
      body != null -> blockText = body.text
      else -> blockText = null
    }
    return blockText
  }

  private fun qualifyThisExpressions(lambdaExpression: PsiLambdaExpression,
                                     psiElementFactory: PsiElementFactory,
                                     blockFromText: PsiCodeBlock) {
    ChangeContextUtil.encodeContextInfo(blockFromText, true)
    val thisClass = RefactoringChangeUtil.getThisClass(lambdaExpression)
    val thisClassName = if (thisClass != null && thisClass !is PsiSyntheticClass) thisClass.name else null
    if (thisClassName != null) {
      val thisAccessExpr = RefactoringChangeUtil.createThisExpression(lambdaExpression.manager, thisClass)
      ChangeContextUtil.decodeContextInfo(blockFromText, thisClass, thisAccessExpr)
      val replacements = HashSet<PsiExpression>()
      blockFromText.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitClass(aClass: PsiClass) {}

        override fun visitSuperExpression(expression: PsiSuperExpression) {
          super.visitSuperExpression(expression)
          if (expression.qualifier == null) {
            replacements.add(expression)
          }
        }

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
          super.visitMethodCallExpression(expression)
          if (thisAccessExpr != null) {
            val psiMethod = expression.resolveMethod()
            val methodExpression = expression.methodExpression
            if (psiMethod != null && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && methodExpression.qualifierExpression == null) {
              replacements.add(expression)
            }
          }
        }
      })
      for (expression in replacements) {
        when (expression) {
          is PsiSuperExpression -> expression.replace(psiElementFactory.createExpressionFromText(thisClassName + "." + expression.getText(), expression))
          is PsiMethodCallExpression -> expression.methodExpression.qualifierExpression = thisAccessExpr
          else -> LOG.error("Unexpected expression")
        }
      }
    }
  }

  private fun isConvertible(lambdaExpression: PsiLambdaExpression): Boolean {
    val thisClass = PsiTreeUtil.getParentOfType(lambdaExpression, PsiClass::class.java, true)
    if (thisClass == null || thisClass is PsiAnonymousClass) {
      val body = lambdaExpression.body ?: return false
      val disabled = BooleanArray(1)
      body.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitThisExpression(expression: PsiThisExpression) {
          disabled[0] = true
        }

        override fun visitSuperExpression(expression: PsiSuperExpression) {
          disabled[0] = true
        }
      })
      if (disabled[0]) return false
    }
    val functionalInterfaceType = lambdaExpression.functionalInterfaceType
    if (functionalInterfaceType != null &&
        LambdaUtil.isFunctionalType(functionalInterfaceType)) {
      val interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType)
      if (interfaceMethod != null) {
        val substitutor = LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(functionalInterfaceType))
        if (interfaceMethod.getSignature(substitutor).parameterTypes.any { !PsiTypesUtil.isDenotableType(it) }) return false
        val returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType)
        return PsiTypesUtil.isDenotableType(returnType)
      }
    }

    return false
  }
}