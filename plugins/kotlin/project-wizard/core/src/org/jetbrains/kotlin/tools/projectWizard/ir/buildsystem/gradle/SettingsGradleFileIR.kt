// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle

import kotlinx.collections.immutable.PersistentList
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter

data class SettingsGradleFileIR(
    @NonNls val projectName: String,
    val subProjects: List<String>,
    override val irs: PersistentList<BuildSystemIR>,
    val plugins: List<BuildSystemPluginIR>
) : FileIR, GradleIR {
    override fun withReplacedIrs(irs: PersistentList<BuildSystemIR>): SettingsGradleFileIR = copy(irs = irs)

    override fun GradlePrinter.renderGradle() {
        irsOfTypeOrNull<PluginManagementIR>()?.let { pluginManagementIrs ->
            sectionCall("pluginManagement", needIndent = true) {
                pluginManagementIrs.irsOfTypeOrNull<PluginManagementRepositoryIR>()?.let { repositories ->
                    sectionCall("repositories") {
                        repositories.distinctAndSorted().listNl()
                    }
                }
                val freeIrs = freeIrs().removeSingleIRDuplicates()
                if (freeIrs.isNotEmpty()) {
                    nl()
                    freeIrs.listNl()
                }
            }
        }
        nl(lineBreaks = 2)

        // Note: plugins/pluginManagement need to appear before anything else
        if (plugins.isNotEmpty()) {
            sectionCall("plugins", needIndent = true) {
                plugins.forEach { it.render(this) }
            }
            nl(lineBreaks = 2)
        }

        if (subProjects.isNotEmpty()) {
            subProjects.list(separator = { nl() }) { subProject ->
                +"include("; +subProject.quotified; +")"
            }
            nl(lineBreaks = 2)
        }

        +"rootProject.name = "; +projectName.quotified
    }
}