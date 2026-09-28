// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfoStorage
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.refactoring.pullUp.canPullUpTo
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperTypeEntry

class K2SuperTypeMemberInfoTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testTargetSuperTypeIsDisabled() {
        val file = myFixture.configureByText(
            "Source.kt",
            """
                interface Base
                interface Other
                class Source : Base, Other
            """.trimIndent()
        ) as KtFile
        val classes = file.declarations.filterIsInstance<KtClass>()
        val sourceClass = classes.single { it.name == "Source" }
        val targetClass = classes.single { it.name == "Base" }
        val storage = KotlinMemberInfoStorage(sourceClass)
        val superTypes = storage.getClassMemberInfos(sourceClass).filterIsInstance<KotlinMemberInfo.SuperType>()
        val targetEntry = superTypes.single { it.superTypeEntry.text == "Base" }
        val otherEntry = superTypes.single { it.superTypeEntry.text == "Other" }

        assertFalse(targetEntry.canPullUpTo(targetClass, storage))
        assertTrue(otherEntry.canPullUpTo(targetClass, storage))
    }

    fun testDelegatedInterfaceIsNotSelected() {
        val file = myFixture.configureByText(
            "Source.kt",
            """
                interface Base {
                    fun action()
                }

                interface Other

                class Source(private val delegate: Base) : Base by delegate, Other
            """.trimIndent()
        ) as KtFile
        val sourceClass = file.declarations.filterIsInstance<KtClass>().single { it.name == "Source" }

        val member = extractClassMembers(sourceClass).single { it.isSuperClass } as KotlinMemberInfo.SuperType

        assertTrue(member.superTypeEntry is KtSuperTypeEntry)
        assertEquals("Other", member.superTypeEntry.text)
        assertEquals(false, member.overrides)
        assertTrue(member.canPullUpTo())
    }

    fun testClassSuperTypeIsDisabled() {
        val file = myFixture.configureByText(
            "Source.kt",
            """
                open class Base
                class Source : Base()
            """.trimIndent()
        ) as KtFile
        val classes = file.declarations.filterIsInstance<KtClass>()
        val sourceClass = classes.single { it.name == "Source" }
        val member = KotlinMemberInfo.SuperType(sourceClass.superTypeListEntries.single())

        assertFalse(member.canPullUpTo())
    }
}
