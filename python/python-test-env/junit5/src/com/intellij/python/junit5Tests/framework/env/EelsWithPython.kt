package com.intellij.python.junit5Tests.framework.env

import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import org.jetbrains.annotations.TestOnly


/**
 * WSL distro and docker image that are expected to have a python
 */
@TestOnly
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@EelSource
@DockerTest("python:3.14.2-trixie", mandatory = false)
@WslTest("ubuntu", mandatory = false)
annotation class EelsWithPython