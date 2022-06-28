/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.performance.tests.utils

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object BuildDataProvider {
    fun getBuildDataFromTeamCity(): BuildData? {
        val teamcityConfig = System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE") ?: return null
        val buildProperties = Properties()
        buildProperties.load(FileInputStream(teamcityConfig))

        val buildId = buildProperties["teamcity.build.id"].toString().toInt()
        val agentName = buildProperties["agent.name"].toString()
        val buildConfigurationId = buildProperties["teamcity.buildType.id"].toString()
        val buildConfigurationName = buildProperties["teamcity.buildConfName"].toString()
        val projectName = buildProperties["teamcity.projectName"].toString()

        var buildBranch: String? = (buildProperties["teamcity.build.branch"] ?: System.getProperty("teamcity.build.branch"))?.toString()
        buildBranch = buildBranch?.takeIf { it != "<default>" } ?: getBuildBranchByGit()
        check(buildBranch != null && buildBranch != "<default>") { "buildBranch='$buildBranch' is expected to be set by TeamCity" }

        val commit = (buildProperties["build.vcs.number"] ?: System.getProperty("build.vcs.number"))?.toString() ?: getCommitByGit()

        return BuildData(
            buildId,
            agentName,
            buildConfigurationId,
            buildConfigurationName,
            projectName,
            buildBranch,
            commit,
            getBuildTimestamp(),
        )
    }

    fun getLocalBuildData(): BuildData {
        return BuildData(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            "LOCAL",
            buildConfigurationId = "",
            buildConfigurationName = "",
            projectName = "",
            buildBranch = getBuildBranchByGit() ?: "NO_BRANCH_INFO",
            commit = getCommitByGit(),
            buildTimestamp = getBuildTimestamp(),
        )
    }

    fun getBuildBranchByGit(): String? {
        var buildBranch = runGit("branch", "--show-current")
        if (buildBranch == null || buildBranch == "<default>") {
            val gitPath = System.getenv("TEAMCITY_GIT_PATH") ?: "git"
            val processBuilder = ProcessBuilder(gitPath, "branch", "--show-current")
            val process = processBuilder.start()
            var line: String?
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                line = reader.readLine()
            }
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                buildBranch = line
            }
        }
        return buildBranch
    }

    fun getCommitByGit(): String? {
        return runGit("rev-parse", "HEAD")
    }

    fun getBuildTimestamp(): String {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return simpleDateFormat.format(Date())
    }

    private fun runGit(vararg extraArgs: String): String? {
        val gitPath = System.getenv("TEAMCITY_GIT_PATH") ?: "git"
        val args = listOf(gitPath) + extraArgs
        val processBuilder = ProcessBuilder(*args.toTypedArray())
        val process = processBuilder.start()
        var line: String?
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            line = reader.readLine()
        }
        var value: String? = null
        if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
            value = line
        }
        return value
    }

}

data class BuildData(
    val buildId: Int,
    val agentName: String,
    val buildConfigurationId: String,
    val buildConfigurationName: String,
    val projectName: String,
    val buildBranch: String,
    val commit: String?,
    val buildTimestamp: String,
)