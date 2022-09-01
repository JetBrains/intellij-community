// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem

import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.BuildFilePrinter
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.MavenPrinter
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.CustomMavenRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository

interface RepositoryWrapper {
    val repository: Repository
}

fun <W : RepositoryWrapper> List<W>.distinctAndSorted() =
    distinctBy(RepositoryWrapper::repository)
        .sortedBy { it.repository.url }
        .sortedBy { wrapper ->
            if (wrapper.repository is DefaultRepository) 0 else 1
        }

data class RepositoryIR(override val repository: Repository) : BuildSystemIR, RepositoryWrapper {
    override fun BuildFilePrinter.render() = when (this) {
        is GradlePrinter -> when (repository) {
            is DefaultRepository -> {
                +repository.type.gradleName
                +"()"
            }
            is CustomMavenRepository -> {
                val url = repository.url.quotified
                when (dsl) {
                    GradlePrinter.GradleDsl.KOTLIN -> +"maven(${url})"
                    GradlePrinter.GradleDsl.GROOVY -> +"maven { url ${url} }"
                }
            }
            else -> Unit
        }
        is MavenPrinter -> when (repository) {
            DefaultRepository.MAVEN_LOCAL -> {}
            else -> node("repository") {
                singleLineNode("id") { +repository.idForMaven }
                singleLineNode("url") { +repository.url }
            }
        }
        else -> Unit
    }
}


data class AllProjectsRepositoriesIR(val repositoriesIR: List<RepositoryIR>) : BuildSystemIR, GradleIR, FreeIR {
    override fun GradlePrinter.renderGradle() {
        sectionCall("allprojects", needIndent = true) {
            sectionCall("repositories") {
                repositoriesIR.listNl()
            }
        }
    }
}