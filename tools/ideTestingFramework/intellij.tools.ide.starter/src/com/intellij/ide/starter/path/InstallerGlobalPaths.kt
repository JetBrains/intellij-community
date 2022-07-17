package com.intellij.ide.starter.path

import com.intellij.ide.starter.utils.Git

class InstallerGlobalPaths : GlobalPaths(Git.getRepoRoot())