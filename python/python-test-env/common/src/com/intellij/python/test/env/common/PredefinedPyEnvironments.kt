// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.python.test.env.conda.condaEnvironment
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.python.test.env.plain.pythonEnvironment
import com.intellij.python.test.env.plain.venvEnvironment
import org.jetbrains.annotations.ApiStatus

/**
 * Predefined Python environments matching those defined in build.gradle.kts.
 * These environments are set up by the Gradle build system.
 */
@ApiStatus.Internal
enum class PredefinedPyEnvironments(val spec: PyEnvironmentSpec<*>) {

  /**
   * Python 2.7.18
   * ID: python2.7
   * Tags: python2.7
   * Packages: virtualenv
   */
  VENV_2_7(venvEnvironment {
    pythonVersion = pythonVersion("2.7")
    libraries {
      +"virtualenv"
    }
  }),

  VANILLA_2_7(pythonEnvironment {
    pythonVersion = pythonVersion("2.7")
  }),

  /**
   * Python 3.8 with extensive testing libraries
   * ID: python3.8
   * Tags: python3.8, python3, ipython, ipython780, skeletons, django, behave, behave-django, tox, jinja2,
   *       packaging, pytest, nose, django-nose, django2, xdist, untangle, pandas, qt (Linux only)
   * Packages: virtualenv, ipython==7.8, django==2.2, behave, jinja2, tox>=2.0, nose, pytest, django-nose,
   *           behave-django, pytest-xdist, untangle, numpy, pandas, pyqt5==5.12, PySide2==5.12.1 (Linux only)
   */
  VENV_3_8_FULL(venvEnvironment {
    pythonVersion = pythonVersion("3.8")
    libraries {
      +"virtualenv"
      +"ipython==7.8"
      +"django==2.2"
      +"behave"
      +"jinja2"
      +"tox>=2.0"
      +"nose"
      +"pytest"
      +"django-nose"
      +"behave-django"
      +"pytest-xdist"
      +"untangle"
      +"numpy"
      +"pandas"
    }
  }),

  /**
   * Python 3.9 with pytest
   * ID: python3.9
   * Tags: python3.9, python3, pytest, xdist, packaging
   * Packages: virtualenv, pytest, pytest-xdist
   */
  VENV_3_9(venvEnvironment {
    pythonVersion = pythonVersion("3.9")
    libraries {
      +"virtualenv"
      +"pytest"
      +"pytest-xdist"
    }
  }),

  /**
   * Python 3.10 minimal
   * ID: python3.10
   * Tags: python3.10, untangle
   * Packages: virtualenv, untangle
   */
  VENV_3_10(venvEnvironment {
    pythonVersion = pythonVersion("3.10")
    libraries {
      +"virtualenv"
      +"untangle"
    }
  }),

  VANILLA_3_11(pythonEnvironment {
    pythonVersion = pythonVersion("3.11")
  }),

  /**
   * Python 3.11 with modern tooling
   * ID: python3.11
   * Tags: python3.11, python3, black, poetry, uv, joblib, tensorflow
   * Packages: virtualenv, black==23.1.0, joblib, tensorflow, poetry, uv
   */
  VENV_3_11(venvEnvironment {
    pythonVersion = pythonVersion("3.11")
    libraries {
      +"virtualenv"
      +"black==23.1.0"
      +"joblib"
      +"tensorflow"
      +"poetry"
      +"uv"
    }
  }),

  VANILLA_3_12(pythonEnvironment {
    pythonVersion = pythonVersion("3.12")
  }),

  /**
   * Python 3.12 with extensive tooling
   * ID: python3.12
   * Tags: python3, poetry, uv, hatch, pipenv, python3.12, messages, twisted, pytest, black-fragments-formatting
   * Packages: virtualenv, teamcity-messages, Twisted, pytest, poetry, uv, hatch, pipenv, black>=23.11.0, pypiwin32 (Windows only)
   */
  VENV_3_12(venvEnvironment {
    pythonVersion = pythonVersion("3.12")
    libraries {
      +"virtualenv"
      +"teamcity-messages"
      +"Twisted"
      +"pytest"
      +"poetry"
      +"uv"
      +"hatch"
      +"pipenv"
      +"black>=23.11.0"
    }
  }),

  /**
   * Python 3.12 with Django (latest)
   * ID: py312_django_latest
   * Tags: python3.12, django, django20, behave, behave-django, django2, pytest, untangle
   * Packages: virtualenv, django, behave-django, behave, pytest, untangle, djangorestframework
   */
  VENV_3_12_DJANGO(venvEnvironment {
    pythonVersion = pythonVersion("3.12")
    libraries {
      +"virtualenv"
      +"django"
      +"behave-django"
      +"behave"
      +"pytest"
      +"untangle"
      +"djangorestframework"
    }
  }),

  VANILLA_3_13(pythonEnvironment {
    pythonVersion = pythonVersion("3.13")
  }),

  /**
   * Python 3.13 with ruff
   * ID: python3.13
   * Tags: python3.13, python3, ruff
   * Packages: virtualenv, ruff
   */
  VENV_3_13(venvEnvironment {
    pythonVersion = pythonVersion("3.13")
    libraries {
      +"virtualenv"
      +"ruff"
    }
  }),

  VANILLA_3_14(pythonEnvironment {
    pythonVersion = pythonVersion("3.14")
  }),

  VENV_3_14(venvEnvironment {
    pythonVersion = pythonVersion("3.14")
    libraries {
      +"virtualenv"
    }
  }),

  /**
   * Conda environment with pinned Miniconda version
   * Tags: conda
   */
  CONDA(condaEnvironment("py312_24.9.2-0") {
    pythonVersion = pythonVersion("3.12")
  });

  companion object {

    /**
     * Environment tags for finding which tags an environment supports
     */
    val ENVIRONMENTS_TO_TAGS: Map<PredefinedPyEnvironments, Set<String>> = mapOf(
      VENV_2_7 to setOf("python2.7"),
      VENV_3_8_FULL to setOf(
        "python3", "python3.8", "pytest", "django", "django2", "behave", "behave-django",
        "ipython", "ipython780", "skeletons", "tox", "jinja2", "packaging", "nose",
        "django-nose", "xdist", "untangle", "pandas"
      ),
      VENV_3_9 to setOf("python3", "python3.9", "pytest", "xdist", "packaging"),
      VENV_3_10 to setOf("python3.10", "untangle"),
      VENV_3_11 to setOf("python3", "python3.11", "black", "poetry", "uv", "joblib", "tensorflow"),
      VENV_3_12 to setOf(
        "python3", "python3.12", "poetry", "uv", "hatch", "pipenv", "pytest",
        "black", "black-fragments-formatting", "messages", "twisted"
      ),
      VENV_3_12_DJANGO to setOf(
        "python3.12", "django", "django2", "django20", "behave", "behave-django",
        "pytest", "untangle"
      ),
      VENV_3_13 to setOf("python3.13", "ruff"),
      VENV_3_14 to setOf("python3", "python3.14", "ruff"),
      VANILLA_3_14 to setOf("vanilla"),
      CONDA to setOf("conda")
    )
  }
}

suspend fun PyEnvironmentFactory.createEnvironment(env: PredefinedPyEnvironments) = createEnvironment(env.spec)