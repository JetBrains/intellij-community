// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.nio.file.Path

/**
 * Tries to find a commit revision for the provided Git tag inside the given repository root.
 */
internal fun findCommitByGitTag(repoRoot: Path, tag: String): String? {
    val output = ProcessBuilder("git", "rev-list", "-n", "1", tag)
        .directory(repoRoot.toFile())
        .outputIfSucceed()

    return output?.takeIf { it.isNotBlank() }
}

internal fun isWorkingTreeClean(repoRoot: Path): Boolean {
    val output = ProcessBuilder("git", "status", "--porcelain")
        .directory(repoRoot.toFile())
        .outputIfSucceed()

    return output?.isBlank() == true
}

internal fun stageAllChanges(repoRoot: Path)  {
    val exitCode = ProcessBuilder("git", "add", "--all")
        .directory(repoRoot.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        exitWithErrorMessage("Failed to stage changes")
    }
}

internal fun commitStagedChanges(repoRoot: Path, title: String, description: String? = null) {
    val exitCode = ProcessBuilder(
        buildList {
            add("git"); add("commit")
            add("-m"); add(title)
            if (description != null) {
                add("-m"); add(description)
            }
        }
    )
        .directory(repoRoot.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        exitWithErrorMessage("Failed to commit changes")
    }
}

/**
 * Fetches the [tag] from `origin` remote of [repoRoot] project
 */
internal fun fetchTag(repoRoot: Path, tag: String) {
    val exitCode = ProcessBuilder("git", "fetch", "origin", "tag", tag)
        .directory(repoRoot.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        exitWithErrorMessage("Failed to fetch tag '$tag'")
    }
}

private fun ProcessBuilder.outputIfSucceed(): String? {
    val process = redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val output = process.inputReader().readText().trim()
    val exitCode = process.waitFor()
    return output.takeIf { exitCode == 0 }
}
