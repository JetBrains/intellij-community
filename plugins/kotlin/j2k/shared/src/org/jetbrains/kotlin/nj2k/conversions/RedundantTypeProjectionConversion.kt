// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.KtClass

class RedundantTypeProjectionConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKTypeParameter) {
            println("\n!!!!====== Found JKTypeParameter ${element.name.value} ${element.parent?.parent}\n")
        }
        if (element !is JKTypeParameterList || element.typeParameters.isEmpty()) return recurse(element)
        println("\nRedundantTypeProjectionConversion, ${element.typeParameters.size}")
        // class CC<T, K> : A() where T : INode?, T : Comparable<in T?>?, K : Node?, K : Collection<out K?>?
        val newTypeParameters = mutableListOf<JKTypeParameter>()
        for (typeParameter in element.typeParameters) {
            println("== ${typeParameter.name.value}")
            val newUpperBounds = mutableListOf<JKTypeElement>()
            for (upperBound in typeParameter.upperBounds) {
                val upperBoundType = upperBound.type
                if (upperBoundType !is JKClassType || upperBoundType.parameters.isEmpty()) {
                    newUpperBounds += upperBound.detached(typeParameter)
                    continue
                }
                val upperBoundTypeDefinition = upperBoundType.classReference.target
                if (upperBoundTypeDefinition !is KtClass || upperBoundTypeDefinition.typeParameters.size != upperBoundType.parameters.size) {
                    newUpperBounds += upperBound.detached(typeParameter)
                    continue
                }

                val newUpperBoundTypeParameters = mutableListOf<JKType>()
                for ((inheritorTypeParameter, parentTypeParameter) in upperBoundType.parameters.zip(upperBoundTypeDefinition.typeParameters)) {
                    println("    inheritorTypeParameter = $inheritorTypeParameter")
                    if (inheritorTypeParameter !is JKVarianceTypeParameterType || inheritorTypeParameter.variance.name.uppercase() != parentTypeParameter.variance.label.uppercase()) {
                        newUpperBoundTypeParameters += inheritorTypeParameter
                        continue
                    }

                    println("    inheritorTypeParameter.boundType.fqName = ${inheritorTypeParameter.boundType.fqName}")
                    newUpperBoundTypeParameters += inheritorTypeParameter.boundType as JKTypeParameterType
                }
                newUpperBounds += JKTypeElement(JKClassType(upperBoundType.classReference, newUpperBoundTypeParameters, upperBoundType.nullability))

                println("  target = ${upperBoundTypeDefinition.javaClass.name}")
                println("  ${upperBoundType.fqName}, ${upperBoundType.parameters.size}")
            }

            newTypeParameters += if (1 == 1) {
                JKTypeParameter(typeParameter::name.detached(), newUpperBounds, typeParameter::annotationList.detached())
            } else {
                typeParameter.detached(element)
            }
        }

        return recurse(JKTypeParameterList(newTypeParameters))
    }
}