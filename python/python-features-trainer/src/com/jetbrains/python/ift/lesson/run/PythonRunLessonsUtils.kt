// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.run

import training.dsl.parseLessonSample

object PythonRunLessonsUtils {
  const val demoConfigurationName = "sandbox"

  val demoSample = parseLessonSample("""
    def find_average(value: list):
        check_input(value)
        result = 0
        for s in value:
            <caret>result += <select id=1>validate_number(extract_number(remove_quotes(s)))</select>
        <caret id=3/>return result
    
    
    def prepare_values():
        return ["'apple 1'", "orange 2", "'tomato 3'"]
    
    
    def extract_number(s):
        return int(<select id=2>s.split()[0]</select>)
    
    
    def check_input(value: list):
        if (value is None) or (len(value) == 0):
            raise ValueError(value)
    
    
    def remove_quotes(s: str):
        if len(s) > 1 and s[0] == "'" and s[-1] == "'":
            return s[1:-1]
        return s
    
    
    def validate_number(number):
        if number < 0:
            raise ValueError(number)
        return number
    
    
    average = find_average(prepare_values())
    print("The average is ", average)
  """.trimIndent())
}
