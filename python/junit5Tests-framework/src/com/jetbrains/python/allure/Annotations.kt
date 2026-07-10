// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.allure

import io.qameta.allure.LabelAnnotation
import java.lang.annotation.Inherited

object Subsystems {

  /**
   *  Represents a subsystem that can be used to annotate tests in Allure
   *
   * @param value The name of the subsystem.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Repeatable
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
  @LabelAnnotation(name = "Subsystem")
  @Inherited
  annotation class Subsystem(val value: String)

  @Subsystem("Refactoring")
  @Inherited
  annotation class Refactoring

  @Subsystem("Quick Fixes")
  @Inherited
  annotation class QuickFixes

  @Subsystem("Code Completion")
  @Inherited
  annotation class CodeCompletion

  @Subsystem("Code Insight")
  @Inherited
  annotation class CodeInsight

  @Subsystem("Inspections")
  @Inherited
  annotation class Inspections

  @Subsystem("LSP Tools")
  @Inherited
  annotation class LspTools

  @Subsystem("Interpreters")
  @Inherited
  annotation class Interpreters

  @Subsystem("Packaging. Requirements")
  @Inherited
  annotation class PackagingRequirements

  @Subsystem("Editing")
  @Inherited
  annotation class Editing

  @Subsystem("Formatter")
  @Inherited
  annotation class Formatter

  @Subsystem("Quick Documentation")
  @Inherited
  annotation class QuickDocumentation

  @Subsystem("IDE")
  @Inherited
  annotation class IDE

  @Subsystem("Debugger")
  @Inherited
  annotation class Debugger

  @Subsystem("Typing")
  @Inherited
  annotation class Typing

  @Subsystem("Python Console")
  @Inherited
  annotation class PythonConsole

  @Subsystem("Parsing")
  @Inherited
  annotation class Parsing

  @Subsystem("Test Runner")
  @Inherited
  annotation class TestRunner

  @Subsystem("Run")
  @Inherited
  annotation class Run

  @Subsystem("Remote Interpreters")
  @Inherited
  annotation class RemoteInterpreters

  @Subsystem("Project Templates")
  @Inherited
  annotation class ProjectTemplates
}

object Components {

  /**
   * Represents a component that can be used to annotate tests in Allure
   *
   * @param value The name or description of the feature.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
  @LabelAnnotation(name = "Component")
  @Inherited
  annotation class Component(val value: String)

  @Component("Postfix")
  @Inherited
  annotation class Postfix

  @Component("Intentions")
  @Inherited
  annotation class Intentions

  @Component("Grazie")
  @Inherited
  annotation class Grazie

  @Component("uv")
  @Inherited
  annotation class Uv

  @Component("Inspection")
  @Inherited
  annotation class Inspection

  @Component("Completion")
  @Inherited
  annotation class Completion

  @Component("Highlighting")
  @Inherited
  annotation class Highlighting

  @Component("Navigation")
  @Inherited
  annotation class Navigation

  @Component("Find Usages")
  @Inherited
  annotation class FindUsages

  @Component("Feature Trainer")
  @Inherited
  annotation class FeatureTrainer

  @Component("Parsing")
  @Inherited
  annotation class Parsing

  @Component("Type Inference")
  @Inherited
  annotation class TypeInference

  @Component("Pyrefly")
  @Inherited
  annotation class Pyrefly

  @Component("Ty")
  @Inherited
  annotation class Ty

  @Component("Inlay Hints")
  @Inherited
  annotation class InlayHints

  @Component("Call Hierarchy")
  @Inherited
  annotation class CallHierarchy

  @Component("Stubs")
  @Inherited
  annotation class Stubs

  @Component("pytest")
  @Inherited
  annotation class Pytest

  @Component("unittest")
  @Inherited
  annotation class Unittest

  @Component("Conda")
  @Inherited
  annotation class Conda

  @Component("Hatch")
  @Inherited
  annotation class Hatch

  @Component("Target")
  @Inherited
  annotation class RemoteTarget

  @Component("SSH")
  @Inherited
  annotation class Ssh

  @Component("Eel")
  @Inherited
  annotation class Eel

  @Component("pip")
  @Inherited
  annotation class Pip

  @Component("Requirements")
  @Inherited
  annotation class Requirements

  @Component("Docstrings")
  @Inherited
  annotation class Docstrings
}

object Layers {

  /**
   * Represents a test layer that can be used to annotate tests in Allure
   *
   * @param value The name or description of the suite.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
  @LabelAnnotation(name = "layer")
  @Inherited
  annotation class Layer(val value: String)

  @Layer("Functional Tests")
  @Inherited
  annotation class Functional

  @Layer("UI Tests")
  @Inherited
  annotation class UI
}
