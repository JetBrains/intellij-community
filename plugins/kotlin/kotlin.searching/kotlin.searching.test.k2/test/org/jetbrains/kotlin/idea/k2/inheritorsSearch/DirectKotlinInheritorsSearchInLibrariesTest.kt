// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inheritorsSearch

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("kotlin.searching/kotlin.searching.test.k2")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../testData/inheritorsSearch/libraries")
class DirectKotlinInheritorsSearchInLibrariesTest : KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    @TestMetadata("interfaceInLibrary.kt")
    fun testDirectInheritorsSearchInLibraries() {
        val libraryInterface = (myFixture.findClass("kotlin.collections.AbstractList") as KtLightClass).kotlinOrigin as KtClass

        val inheritorNames = ActionUtil.underModalProgress(project, "test progress") {
            DirectKotlinClassInheritorsSearch.search(libraryInterface, GlobalSearchScope.allScope(project))
                .asIterable()
                .mapNotNull { (it as? KtClass)?.name }
        }
        assertTrue("B" in inheritorNames)
    }
}