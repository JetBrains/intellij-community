// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.projects

import org.jetbrains.kotlin.idea.project.test.base.ProjectData
import org.jetbrains.kotlin.idea.project.test.base.actions.HighlightFileAction
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.perfTestProjectPath
import org.jetbrains.kotlin.idea.performance.tests.utils.project.ProjectOpenAction

object RustProject {
    /*
      The fork (https://github.com/darthorimar/intellij-rust) of intellij-rust plugin is used
      It has required code pregenerated manually
      Also, it is always the same version of rust plugin codebase to test against
    */
    val project by lazy {
        ProjectData(
          id = "intellijRustPlugin",
          path = perfTestProjectPath("intellij-rust"),
          openAction = ProjectOpenAction.GRADLE_PROJECT,
        )
    }

    val actions: List<ProjectAction> = run {
        val basePath = "src/main/kotlin/org/rust"

        listOf(
            //HighlightFileAction("$basePath/lang/core/stubs/StubImplementations.kt"),
            HighlightFileAction("$basePath/lang/core/resolve/NameResolution.kt"),
            HighlightFileAction("$basePath/ide/annotator/RsErrorAnnotator.kt"),
            HighlightFileAction("$basePath/lang/core/types/infer/TypeInferenceWalker.kt"),
            HighlightFileAction("$basePath/lang/utils/RsDiagnostic.kt"),
            HighlightFileAction("$basePath/lang/core/resolve/ImplLookup.kt"),
            HighlightFileAction("$basePath/lang/core/macros/MacroExpansionManager.kt"),
            HighlightFileAction("$basePath/cargo/util/CargoOptions.kt"),
            HighlightFileAction("$basePath/lang/core/types/infer/TypeInference.kt"),
            HighlightFileAction("$basePath/lang/core/CompilerFeatures.kt"),
            HighlightFileAction("$basePath/lang/core/macros/ExpandedMacroStorage.kt"),
            HighlightFileAction("$basePath/lang/core/psi/ext/RsQualifiedNamedElement.kt"),
            HighlightFileAction("$basePath/ide/presentation/RsPsiRenderer.kt"),
            HighlightFileAction("$basePath/cargo/project/workspace/CargoWorkspace.kt"),
            HighlightFileAction("$basePath/cargo/project/model/impl/CargoProjectImpl.kt"),
            HighlightFileAction("$basePath/lang/core/resolve2/FacadeResolve.kt"),
            HighlightFileAction("$basePath/lang/core/resolve2/DefCollector.kt"),
            HighlightFileAction("$basePath/lang/core/psi/RsPsiFactory.kt"),
            HighlightFileAction("$basePath/lang/core/parser/RustParserUtil.kt"),
            HighlightFileAction("$basePath/lang/core/macros/MacroExpansionTask.kt"),
            HighlightFileAction("$basePath/cargo/toolchain/tools/Cargo.kt"),
        )
    }
}