// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.openapi.application.ApplicationNamesInfo
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ift.lesson.assistance.PythonEditorCodingAssistanceLesson
import com.jetbrains.python.ift.lesson.basic.PythonContextActionsLesson
import com.jetbrains.python.ift.lesson.basic.PythonSelectLesson
import com.jetbrains.python.ift.lesson.basic.PythonSurroundAndUnwrapLesson
import com.jetbrains.python.ift.lesson.completion.*
import com.jetbrains.python.ift.lesson.essensial.PythonOnboardingTour
import com.jetbrains.python.ift.lesson.navigation.PythonDeclarationAndUsagesLesson
import com.jetbrains.python.ift.lesson.navigation.PythonFileStructureLesson
import com.jetbrains.python.ift.lesson.navigation.PythonRecentFilesLesson
import com.jetbrains.python.ift.lesson.navigation.PythonSearchEverywhereLesson
import com.jetbrains.python.ift.lesson.refactorings.PythonInPlaceRefactoringLesson
import com.jetbrains.python.ift.lesson.refactorings.PythonQuickFixesRefactoringLesson
import com.jetbrains.python.ift.lesson.refactorings.PythonRefactorMenuLesson
import com.jetbrains.python.ift.lesson.refactorings.PythonRenameLesson
import com.jetbrains.python.ift.lesson.run.PythonDebugLesson
import com.jetbrains.python.ift.lesson.run.PythonRunConfigurationLesson
import training.dsl.LessonUtil
import training.learn.CourseManager
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.LocalHistoryLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.navigation.FindInFilesLesson
import training.learn.lesson.general.refactorings.ExtractMethodCocktailSortLesson
import training.learn.lesson.general.refactorings.ExtractVariableFromBubbleLesson

class PythonLearningCourse : LearningCourseBase(PythonLanguage.INSTANCE.id) {
  override fun modules() = onboardingTour() + stableModules() + CourseManager.instance.findCommonModules("Git")

  private val disableOnboardingLesson get() = ApplicationNamesInfo.getInstance().fullProductNameWithEdition.equals("PyCharm Edu")

  private fun onboardingTour() = if (!disableOnboardingLesson) listOf(
    LearningModule(id = "Python.Onboarding",
                   name = PythonLessonsBundle.message("python.onboarding.module.name"),
                   description = PythonLessonsBundle.message("python.onboarding.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(PythonOnboardingTour())
    }
  )
  else emptyList()

  private fun stableModules() = listOf(
    LearningModule(id = "Python.EditorBasics",
                   name = LessonsBundle.message("editor.basics.module.name"),
                   description = LessonsBundle.message("editor.basics.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        PythonContextActionsLesson(),
        GotoActionLesson(ls("Actions.py.sample")),
        PythonSelectLesson(),
        CommentUncommentLesson(ls("Comment.py.sample")),
        DuplicateLesson(ls("Duplicate.py.sample")),
        MoveLesson("accelerate", ls("Move.py.sample")),
        CollapseLesson(ls("Collapse.py.sample")),
        PythonSurroundAndUnwrapLesson(),
        MultipleSelectionHtmlLesson(),
      )
    },
    LearningModule(id = "Python.CodeCompletion",
                   name = LessonsBundle.message("code.completion.module.name"),
                   description = LessonsBundle.message("code.completion.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      listOf(
        PythonBasicCompletionLesson(),
        PythonTabCompletionLesson(),
        PythonPostfixCompletionLesson(),
        PythonSmartCompletionLesson(),
        FStringCompletionLesson(),
      )
    },
    LearningModule(id = "Python.Refactorings",
                   name = LessonsBundle.message("refactorings.module.name"),
                   description = LessonsBundle.message("refactorings.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("Refactorings/$sampleName")
      listOf(
        PythonRefactorMenuLesson(),
        PythonRenameLesson(),
        ExtractVariableFromBubbleLesson(ls("ExtractVariable.py.sample")),
        ExtractMethodCocktailSortLesson(ls("ExtractMethod.py.sample")),
        PythonQuickFixesRefactoringLesson(),
        PythonInPlaceRefactoringLesson(),
      )
    },
    LearningModule(id = "Python.CodeAssistance",
                   name = LessonsBundle.message("code.assistance.module.name"),
                   description = LessonsBundle.message("code.assistance.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
      listOf(
        LocalHistoryLesson(),
        CodeFormatLesson(ls("CodeFormat.py.sample"), true),
        ParameterInfoLesson(ls("ParameterInfo.py.sample")),
        QuickPopupsLesson(ls("QuickPopups.py.sample")),
        PythonEditorCodingAssistanceLesson(ls("EditorCodingAssistance.py.sample")),
      )
    },
    LearningModule(id = "Python.Navigation",
                   name = LessonsBundle.message("navigation.module.name"),
                   description = LessonsBundle.message("navigation.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(
        PythonSearchEverywhereLesson(),
        FindInFilesLesson("src/warehouse/find_in_files_sample.py"),
        PythonDeclarationAndUsagesLesson(),
        PythonFileStructureLesson(),
        PythonRecentFilesLesson(),
      )
    },
    LearningModule(id = "Python.RunAndDebug",
                   name = LessonsBundle.message("run.debug.module.name"),
                   description = LessonsBundle.message("run.debug.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      listOf(
        PythonRunConfigurationLesson(),
        PythonDebugLesson(),
      )
    },
  )
}