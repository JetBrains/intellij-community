package com.intellij.python.junit5Tests.framework.env

import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.junit5.RunOnEnvironmentsExtension
import org.junit.jupiter.api.ClassTemplate
import org.junit.jupiter.api.extension.ClassTemplateInvocationLifecycleMethod
import org.junit.jupiter.api.extension.ExtendWith

@PyEnvTestCase
@ClassTemplate
@ClassTemplateInvocationLifecycleMethod(classTemplateAnnotation = PyEnvTestCase::class, lifecycleMethodAnnotation = BeforeRunOnEnvironmentInvocation::class)
@ExtendWith(
  RunOnEnvironmentsExtension::class,
)
@Target(AnnotationTarget.CLASS)
annotation class RunOnEnvironments(vararg val envs: PredefinedPyEnvironments = [PredefinedPyEnvironments.VENV_3_12])
