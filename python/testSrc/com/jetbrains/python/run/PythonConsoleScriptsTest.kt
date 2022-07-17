// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.testFramework.LightPlatform4TestCase
import org.assertj.core.api.SoftAssertions
import org.junit.Test

class PythonConsoleScriptsTest : LightPlatform4TestCase() {
  @Test
  internal fun `test buildScriptWithConsoleRun`() {
    val pythonConfigurationFactory = PythonConfigurationType.getInstance().factory

    SoftAssertions.assertSoftly { softly ->
      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script.py"
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("runfile('script.py')")
          .describedAs("Generates the line that executes the script")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script name with spaces.py"
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("runfile('script name with spaces.py')")
          .describedAs("Generates the line that executes the script with spaces in its name")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script's name.py"
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("runfile('script\\'s name.py')")
          .describedAs("Generates the line that executes the script with a single quotes in its name")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script.py"
        runConfiguration.workingDirectory = "/home/username"
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("runfile('script.py', wdir='/home/username')")
          .describedAs("Generates the line that executes the script with working directory specified")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script.py"
        runConfiguration.workingDirectory = "/home/username"
        runConfiguration.scriptParameters = "simple \"one parameter in four words\" \"let's make it harder\""
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo(
            "runfile('script.py', args=['simple', 'one parameter in four words', 'let\\'s make it harder'], wdir='/home/username')")
          .describedAs("Generates the line that executes the script with working directory and parameters specified")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "user_module"
        runConfiguration.isModuleMode = true
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("runfile('user_module', is_module=True)")
          .describedAs("Generates the line that executes the module")
      }

      PythonRunConfiguration(project, pythonConfigurationFactory).let { runConfiguration ->
        runConfiguration.scriptName = "script.py"
        runConfiguration.envs["FOO"] = "BAR"
        runConfiguration.envs["BAZ"] = "qux"
        runConfiguration.envs["QUUX"] = "Corge's Grault"
        softly
          .assertThat(buildScriptWithConsoleRun(runConfiguration))
          .isEqualTo("""|import os
                        |os.environ['FOO'] = 'BAR'
                        |os.environ['BAZ'] = 'qux'
                        |os.environ['QUUX'] = 'Corge\'s Grault'
                        |runfile('script.py')""".trimMargin())
          .describedAs("Generates the line that executes the script with environment variables specified")
      }
    }
  }
}