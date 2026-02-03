package com.intellij.ide.starter.sdk

import java.nio.file.Path

data class JdkItemPaths(@JvmField val homePath: Path, @JvmField val installPath: Path)