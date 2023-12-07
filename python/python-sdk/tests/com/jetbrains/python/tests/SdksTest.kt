package com.jetbrains.python.tests

import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.SdksKeeper
import com.jetbrains.python.sdk.Product
import org.junit.Test

class SdksTest {
  @Test
  fun testLoading() {
    assert(SdksKeeper.pythonReleasesByLanguageLevel().isNotEmpty())
  }

  @Test
  fun testPyPy() {
    val releases = SdksKeeper.pythonReleasesByLanguageLevel()[LanguageLevel.PYTHON310]
    assert(releases!!.any { it.product == Product.PyPy })
  }

  @Test
  fun testCPythonAvailableFor311and312() {
    val releases = SdksKeeper.pythonReleasesByLanguageLevel()

    fun filter(os: OS, cpuArch: CpuArch): Set<LanguageLevel> {
      return releases.filterValues { releases ->
        releases.any { r ->
          r.product == Product.CPython &&
          r.binaries?.any { b -> b.os == os && b.cpuArch?.equals(cpuArch) != false } ?: false
        }
      }.keys
    }

    val mandatory = listOf(LanguageLevel.PYTHON311, LanguageLevel.PYTHON312)
    for (os in listOf(OS.macOS, OS.Windows)) {
      for (cpuArch in listOf(CpuArch.X86, CpuArch.X86_64, CpuArch.ARM64)) {
        val missed = mandatory - filter(os, cpuArch)
        assert(missed.isEmpty()) { "Have no ${os} [${cpuArch}] distributive for version: ${missed}" }
      }
    }
  }
}