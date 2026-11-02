/*
 * Copyright 2010-2021 JetBrains s.r.o.
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

package org.jetbrains.kotlin.config

object SettingConstants {
    const val KOTLIN_COMMON_COMPILER_ARGUMENTS_SECTION: String = "KotlinCommonCompilerArguments"
    const val KOTLIN_TO_JS_COMPILER_ARGUMENTS_SECTION: String = "Kotlin2JsCompilerArguments"
    const val KOTLIN_TO_WASM_COMPILER_ARGUMENTS_SECTION: String = "KotlinWasmCompilerArguments"
    const val KOTLIN_TO_JVM_COMPILER_ARGUMENTS_SECTION: String = "Kotlin2JvmCompilerArguments"
    const val KOTLIN_COMPILER_SETTINGS_SECTION: String = "KotlinCompilerSettings"
    const val KOTLIN_JPS_PLUGIN_SETTINGS_SECTION: String = "KotlinJpsPluginSettings"
    const val KOTLIN_COMPILER_SETTINGS_FILE: String = "kotlinc.xml"
    const val KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID: String = "kotlin.compiler.ref.index"
    const val KOTLIN_DATA_CONTAINER_ID: String = "kotlin-data-container"
}
