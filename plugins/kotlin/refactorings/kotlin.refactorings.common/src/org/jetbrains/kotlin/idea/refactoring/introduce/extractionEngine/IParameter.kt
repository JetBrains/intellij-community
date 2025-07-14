// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

interface IParameter<KotlinType> {
    val argumentText: String
    val name: String
    val mirrorVarName: String?
    val receiverCandidate: Boolean
    val contextParameter: Boolean

    val parameterType: KotlinType

    fun getParameterTypeCandidates(): List<KotlinType>
}

val IParameter<*>.nameForRef: String get() = mirrorVarName ?: name

interface IMutableParameter<KotlinType> : IParameter<KotlinType> {
    var refCount: Int
}
