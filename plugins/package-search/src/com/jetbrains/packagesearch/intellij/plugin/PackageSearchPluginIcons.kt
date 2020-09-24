package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.openapi.util.IconLoader

object PackageSearchPluginIcons {
    val Artifact by lazy { IconLoader.getIcon("/icons/artifact.svg") }
    val ArtifactSmall by lazy { IconLoader.getIcon("/icons/artifactSmall.svg") }
    val Package by lazy { IconLoader.getIcon("/icons/package.svg") }
    val StackOverflow by lazy { IconLoader.getIcon("/icons/stackOverflow.svg") }

    object Operations {
        val Upgrade by lazy { IconLoader.getIcon("/icons/operations/upgrade.svg") }
        val Downgrade by lazy { IconLoader.getIcon("/icons/operations/downgrade.svg") }
        val Install by lazy { IconLoader.getIcon("/icons/operations/install.svg") }
        val Remove by lazy { IconLoader.getIcon("/icons/operations/remove.svg") }
    }
}
