// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

typealias KotlinKPMAttributeKeyId = String
typealias KotlinKPMAttributeValueId = String
typealias KotlinKPMVariantAttributesMap = Map<KotlinKPMAttributeKeyId, KotlinKPMAttributeValueId>

interface KotlinVariant: KotlinFragment {
    val variantAttributes: KotlinKPMVariantAttributesMap
    val compilationOutputs: KotlinCompilationOutput?
}
