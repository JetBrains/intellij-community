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

package com.jetbrains.completion.feature.impl

import com.jetbrains.completion.feature.BinaryFeature
import com.jetbrains.completion.feature.ex.UnexpectedBinaryValueException

/**
 * @author Vitaliy.Bibaev
 */
class BinaryFeatureImpl(override val name: String,
                        override val index: Int,
                        override val undefinedIndex: Int,
                        override val defaultValue: Double,
                        private val firstValue: BinaryFeature.BinaryValueDescriptor,
                        private val secondValue: BinaryFeature.BinaryValueDescriptor) : BinaryFeature {
    private fun transform(value: String): Double = when (value) {
        firstValue.key -> firstValue.mapped
        secondValue.key -> secondValue.mapped
        else -> throw UnexpectedBinaryValueException(name, value, setOf(firstValue.key, secondValue.key))
    }

    override val availableValues: Pair<String, String> = firstValue.key to secondValue.key

    override fun process(value: Any, featureArray: DoubleArray) {
        featureArray[undefinedIndex] = 0.0
        featureArray[index] = transform(value.toString())
    }

    override fun setDefaults(featureArray: DoubleArray) {
        featureArray[undefinedIndex] = 1.0
        featureArray[index] = defaultValue
    }
}