// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.completion

import training.dsl.parseLessonSample
import training.learn.lesson.general.completion.BasicCompletionLessonBase

private const val keyToComplete1 = "director"
private const val keyToComplete2 = "distributor"

class PythonBasicCompletionLesson : BasicCompletionLessonBase() {
  override val sample1 = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'demo': False,
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }
    
    
    def $keyToComplete1():
        return movies_dict[<caret>]
  """.trimIndent())

  override val sample2 = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'demo': False,
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }
    
    
    def $keyToComplete1():
        return movies_dict['$keyToComplete1']


    def $keyToComplete2():
        return movies_dict['<caret>']
  """.trimIndent())

  override val item1StartToType = "'dir"
  override val item1CompletionPrefix = "'$keyToComplete1"
  override val item1CompletionSuffix = "'"

  override val item2Completion = "'$keyToComplete2'"
  override val item2Inserted = keyToComplete2

  //override val existedFile = PythonLangSupport.sandboxFile
}
