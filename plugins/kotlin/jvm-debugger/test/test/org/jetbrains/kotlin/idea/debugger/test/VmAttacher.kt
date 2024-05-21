/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment

private const val PROVIDER_ENV = "INTELLIJ_DEBUGGER_TESTS_VM_ATTACHER"
private const val PROVIDER_PROPERTY = "intellij.debugger.tests.vm.attacher"

/** Attaches to a VM */
interface VmAttacher {
    /** Perform any setup operations */
    fun setUp() {}

    /** Perform any tear down operations */
    fun tearDown() {}

    fun attachVirtualMachine(
        testCase: KotlinDescriptorTestCase,
        javaParameters: JavaParameters,
        environment: ExecutionEnvironment
    ): DebuggerSession

    companion object {
        /**
         * Returns a [VmAttacher]
         *
         * The return value is determined from the environment or a JVM property. The property can be a preset value (for example `art` or
         * a class name that is expected to be in the classpath).
         *
         * The default is a [JvmAttacher].
         */
        fun getInstance(): VmAttacher {
            return when (val provider = System.getProperty(PROVIDER_PROPERTY) ?: System.getenv(PROVIDER_ENV) ?: "jvm") {
                "jvm" -> JvmAttacher()
                "art" -> ArtAttacher()
                else -> loadAttacher(provider)
            }
        }
    }
}

/**
 * Load a [VmAttacher] by FQN from the classpath
 *
 * This is useful for 3rd party plugin devs that run on a non-standard VM to run this test suite using it instead of the standard.
 *
 * For example, Android plugin runs the tests on an ART VM. While there is a [ArtAttacher] included in this commit, Google wants to be able
 * to iterate quickly with any changes that are needed as bugs are fixed. Having to keep the `ArtAttacher` in sync constantly is cumbersome.
 */
private fun loadAttacher(className: String): VmAttacher {
    // Exceptions will cause a test failure
    val providerClass = ClassLoader.getSystemClassLoader().loadClass(className)
    val constructor = providerClass.getDeclaredConstructor()
    return constructor.newInstance() as VmAttacher
}
