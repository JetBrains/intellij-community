// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.promoter

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.java.ift.javaLanguageId
import com.intellij.java.ift.lesson.essential.ideaOnboardingLessonId
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.training.ift.KotlinLessonsBundle
import org.jetbrains.kotlin.training.ift.kotlinLanguageId
import training.ui.welcomeScreen.OnboardingLessonPromoter
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent

class JavaOrKotlinOnboardingTourPromoter : OnboardingLessonPromoter(
    ideaOnboardingLessonId, javaLanguageId, JavaLessonsBundle.message("java.onboarding.lesson.name")
) {
    // show this promoter instead of Java onboarding tour promoter
    override fun getPriorityLevel(): Int = StartPagePromoter.PRIORITY_LEVEL_NORMAL + 10

    override val promoImage: Icon
        get() = IconLoader.getIcon("img/idea-onboarding-tour.png", JavaLessonsBundle::class.java.classLoader)

    override fun createButton(): JComponent {
        val javaOnboardingAction = StartOnboardingAction(javaLanguageId, KotlinLessonsBundle.message("welcome.promo.start.tour.java"))
        val kotlinOnboardingAction = StartOnboardingAction(kotlinLanguageId, KotlinLessonsBundle.message("welcome.promo.start.tour.kotlin"))
        val button = JBOptionButton(javaOnboardingAction, arrayOf(javaOnboardingAction, kotlinOnboardingAction)).also {
            it.addSeparator = false
            it.showPopupYOffset = 1 // visually, it will be 4, because of the empty 3px bottom border of the button
        }
        return button
    }

    private inner class StartOnboardingAction(
        private val languageId: String,
        name: @Nls String
    ) : AbstractAction(name) {
        override fun actionPerformed(e: ActionEvent?) {
            startOnboardingLessonWithSdk(lessonId, languageId)
        }
    }
}