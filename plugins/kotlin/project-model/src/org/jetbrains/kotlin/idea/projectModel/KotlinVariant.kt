// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

//TODO use mapping to Long keys
typealias KotlinKPMAttributeKeyId = String
typealias KotlinKPMAttributeValueId = String
typealias KotlinKPMVariantAttributesMap = Map<KotlinKPMAttributeKeyId, KotlinKPMAttributeValueId>

interface KotlinVariantData : Serializable {
    val variantAttributes: KotlinKPMVariantAttributesMap
    val compilationOutputs: KotlinCompilationOutput?
}

typealias KotlinVariant = Pair<KotlinFragment, KotlinVariantData>