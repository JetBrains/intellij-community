// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Subsystem("Interpreters")
  @Inherited
  annotation class Interpreters

  @Subsystem("Packaging. Requirements")
  @Inherited
  annotation class PackagingRequirements
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
