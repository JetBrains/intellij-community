// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.completion

import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.LearningDslBase
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.completion.PostfixCompletionLesson

class PythonPostfixCompletionLesson : PostfixCompletionLesson() {
  override val sample: LessonSample = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }

    movies_dict.get('year')<caret>
  """.trimIndent())

  override val result: String = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }

    if movies_dict.get('year') is not None:
        <caret>
  """.trimIndent()).text

  override val completionSuffix: String = ".if"
  override val completionItem: String = "ifnn"

  override fun LearningDslBase.getTypeTaskText(): String {
    return PythonLessonsBundle.message("python.postfix.completion.type", code(completionSuffix))
  }

  override fun LearningDslBase.getCompleteTaskText(): String {
    return PythonLessonsBundle.message("python.postfix.completion.complete", code(completionItem), action("EditorChooseLookupItem"))
  }
}