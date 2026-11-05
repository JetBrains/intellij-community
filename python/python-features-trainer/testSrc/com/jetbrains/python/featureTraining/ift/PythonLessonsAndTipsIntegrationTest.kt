package com.jetbrains.python.featureTraining.ift

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.python.featuresTrainer.ift.PythonLangSupport
import com.intellij.python.featuresTrainer.ift.PythonLearningCourse
import com.intellij.testFramework.junit5.TestApplication
import training.lang.LangSupport
import training.simple.LessonsAndTipsIntegrationTest

@TestApplication
@Subsystems.IDE
@Components.FeatureTrainer
@Layers.Functional
class PythonLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId = "Python"
  override val languageSupport: LangSupport? = PythonLangSupport()
  override val learningCourse = PythonLearningCourse()
}