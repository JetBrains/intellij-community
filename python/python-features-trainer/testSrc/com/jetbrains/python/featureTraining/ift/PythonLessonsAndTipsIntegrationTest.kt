package com.jetbrains.python.featureTraining.ift

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.python.featuresTrainer.ift.PythonLangSupport
import com.intellij.python.featuresTrainer.ift.PythonLearningCourse
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import training.lang.LangSupport
import training.simple.LessonsAndTipsIntegrationTest

@RunWith(JUnit4::class)
@Subsystems.IDE
@Components.FeatureTrainer
@Layers.Functional
class PythonLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId = "Python"
  override val languageSupport: LangSupport? = PythonLangSupport()
  override val learningCourse = PythonLearningCourse()
}