package com.intellij.tools.ide.starter.bazel.project

import com.intellij.testFramework.common.bazel.BazelTestDependencyHttpFileDownloader
import com.intellij.platform.bazel.runfiles.BazelLabel
import java.nio.file.Path

interface StarterBazelTestDependencyDownloader {
  fun getDepsByLabel(label: BazelLabel): Path
}

class DefaultStarterBazelTestDependencyDownloader(private val dependenciesDescriptionFile: Path) : StarterBazelTestDependencyDownloader {
  private var downloader = object : BazelTestDependencyHttpFileDownloader() {
    override val dependenciesDescFile: Path get() = dependenciesDescriptionFile
  }

  override fun getDepsByLabel(label: BazelLabel): Path {
    return downloader.getDepsByLabel(label)
  }
}
