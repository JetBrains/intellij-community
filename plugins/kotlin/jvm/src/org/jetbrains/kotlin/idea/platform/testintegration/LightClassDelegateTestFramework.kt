// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.platform.testintegration

import com.intellij.codeInsight.TestFrameworks
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class LightClassDelegateTestFramework: LightTestFramework {
    override val name: String = "LightClass"

    override fun qualifiedName(namedDeclaration: KtNamedDeclaration): String? = when (namedDeclaration) {
        is KtClassOrObject -> namedDeclaration.toLightClass()?.qualifiedName
        is KtNamedFunction -> {
            val lightMethod = namedDeclaration.toLightMethods().firstOrNull()
            lightMethod?.containingClass.safeAs<KtLightClass>()?.qualifiedName
        }
        else -> null
    }

    override fun detectFramework(namedDeclaration: KtNamedDeclaration): LightTestFrameworkResult = when (namedDeclaration) {
        is KtClassOrObject -> {
            namedDeclaration.toLightClass()?.let { lightClass ->
                TestFrameworks.detectFramework(lightClass)?.takeIf { it.isTestClass(lightClass) }
            }?.let { ResolvedLightTestFrameworkResult(it) } ?: NoLightTestFrameworkResult
        }
        is KtNamedFunction -> {
            namedDeclaration.toLightMethods().firstOrNull()?.let { lightMethod ->
                lightMethod.containingClass.safeAs<KtLightClass>()?.let { lightClass ->
                    TestFrameworks.detectFramework(lightClass)?.takeIf {
                        it.isTestMethod(lightMethod, false)
                    }
                }
            }?.let { ResolvedLightTestFrameworkResult(it) } ?: NoLightTestFrameworkResult
        }
        else -> UnsureLightTestFrameworkResult
    }
}