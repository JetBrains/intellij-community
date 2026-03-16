// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal fun advanceCompilerVersion(preferences: GeneratorPreferences) {
    val ticket = preferences.getTicketAndCheck()

    val newVersion = preferences.newKotlincVersion ?: run {
        println("No '${GeneratorPreferences::newKotlincVersion.name}' preference is provided; entering interactive mode...")
        print("Enter a new version: ")
        readln()
    }

    if (newVersion.isBlank()) {
        exitWithErrorMessage("The new Kotlin compiler version is blank")
    }

    val currentVersion = preferences.kotlincVersion
    if (currentVersion == newVersion) {
        exitWithErrorMessage("The new Kotlin compiler version ($newVersion) is the same as the current one")
    }

    println("Advancing compiler version to '$newVersion'...")

    // Alternatively, to not require the Kotlin repo locally, we might use something like
    // git ls-remote --tags git@github.com:JetBrains/kotlin.git refs/tags/build-2.3.0-dev-9317^{}
    // But it seems unnecessary since there is an assumption that all relevant persons should have the repository cloned locally
    val kotlinRepoRoot = preferences.kotlinCompilerRepositoryRoot
    println("Kotlin compiler repository: $kotlinRepoRoot")

    val currentCommit = findCommitByBuildNumber(kotlinRepoRoot, currentVersion)
    val newCommit = findCommitByBuildNumber(kotlinRepoRoot, newVersion)
    val githubCompareLink = githubCompareLink(currentCommit, newCommit)
    val changesDescription = "Includes: $githubCompareLink"
    println(changesDescription)

    updateProjectAndCommit(
        preferences = preferences,
        modifications = listOf(
            preferences::kotlincArtifactsMode.modify(GeneratorPreferences.ArtifactMode.MAVEN),
            preferences::kotlincVersion.modify(newVersion),
        ),
        commitTitle = "[kotlin] $ticket advance kotlinc version for analyzer to $newVersion",
        commitDescription = changesDescription,
    )
}

internal fun updateProjectAndCommit(
    preferences: GeneratorPreferences,
    modifications: List<PreferenceModification<*>>,
    commitTitle: String,
    commitDescription: String? = null,
) {
    println("Checking whether the working tree is clean...")
    val projectRoot = KotlinTestsDependenciesUtil.projectRoot
    if (!isWorkingTreeClean(projectRoot)) {
        exitWithErrorMessage("The working tree is not clean, commit or discard changes before proceeding")
    }

    updateModelProperties(modifications)

    println("'${GeneratorPreferences.modelPropertiesPath}' is updated, running the project model updater tool...")
    main(
        modifications + listOf(
            preferences::applicationMode.modify(GeneratorPreferences.ApplicationMode.PROJECT_MODEL_UPDATER),
            preferences::convertJpsToBazel.modify(true),
        )
    )

    println("The update is complete, checking the changes...")
    if (isWorkingTreeClean(projectRoot)) {
        exitWithErrorMessage("No changes found in the project")
    }

    println("Changes found, staging the changes...")
    stageAllChanges(projectRoot)

    println("Committing the changes...")
    commitStagedChanges(
        repoRoot = projectRoot,
        title = commitTitle,
        description = commitDescription,
    )

    println("The project has been updated — the commit is ready to push")
}

private fun updateModelProperties(modifications: List<PreferenceModification<*>>) {
    val modelPropertiesPath = GeneratorPreferences.modelPropertiesPath
    val oldContent = modelPropertiesPath.readText()
    val newContent = modifications.fold(oldContent) { content, modification ->
        content.replace(
            oldValue = modification.asOldProperty,
            newValue = modification.asNewProperty,
        )
    }

    modelPropertiesPath.writeText(newContent)
}

private fun findCommitByBuildNumber(repoRoot: Path, buildNumber: String): String {
    println("Searching for a commit matching the '$buildNumber' version in the Kotlin repository...")

    // The format that is used for the Git tags in the Kotlin repository
    val tag = "build-$buildNumber"
    val commit = findCommitByGitTag(repoRoot, tag) ?: run {
        println("Failed to find the '$tag' tag in the Kotlin repository; searching for the tag in the 'origin' remote...")
        fetchTag(repoRoot, tag)

        println("Second attempt to find the '$tag' tag in the Kotlin repository...")
        findCommitByGitTag(repoRoot, tag) ?: exitWithErrorMessage("Git tag '$tag' not found in the Kotlin repository")
    }

    println("Found commit '$commit' for tag '$tag'")
    return commit
}

private fun githubCompareLink(baseRevision: String, newRevision: String): String {
    val shortText = "${baseRevision.subSequence(0, 8)}...${newRevision.subSequence(0, 8)}"
    return "[$shortText](https://github.com/JetBrains/kotlin/compare/$baseRevision...$newRevision)"
}
