/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.universalTests

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage
import com.jetbrains.commandInterface.commandLine.CommandLinePart
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile
import com.jetbrains.commandInterface.commandLine.psi.CommandLineOption
import java.util.*

/**
 * @author Ilya.Kazakevich
 */


//TODO: Migrate to [ParametersListUtil#parse] but support single quotes
/**
 * Emulates command line processor (cmd, bash) by parsing command line to arguments that can be provided as argv.
 * Escape chars are not supported but quotes work.
 * @throws ExecutionException if can't be parsed
 */
fun getParsedAdditionalArguments(project: Project, additionalArguments: String): List<String> {
  val factory = PsiFileFactory.getInstance(project)
  val file = factory.createFileFromText(CommandLineLanguage.INSTANCE,
                                        String.format("fake_command %s", additionalArguments)) as CommandLineFile

  if (file.children.any { it is PsiErrorElement }) {
    throw ExecutionException("Additional arguments can't be parsed. Please check they are valid: $additionalArguments")
  }


  val additionalArgsList = ArrayList<String>()
  var skipArgument = false
  file.children.filterIsInstance(CommandLinePart::class.java).forEach {
    when (it) {
      is CommandLineOption -> {
        val optionText = it.text
        val possibleArgument = it.findArgument()
        if (possibleArgument != null) {
          additionalArgsList.add(optionText + possibleArgument.valueNoQuotes)
          skipArgument = true
        }
        else {
          additionalArgsList.add(optionText)
        }
      }
      is CommandLineArgument -> {
        if (!skipArgument) {
          additionalArgsList.add(it.valueNoQuotes)
        }
        skipArgument = false
      }
    }
  }
  return additionalArgsList
}

