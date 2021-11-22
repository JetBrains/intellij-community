// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.project.test.projects

import org.jetbrains.kotlin.idea.perf.common.*
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

object RustProject {
    /*
      The fork (https://github.com/darthorimar/intellij-rust) of intellij-rust plugin is used
      It has required code pregenerated manually
      Also, it is always the same version of rust plugin codebase to test against
    */
    val project = ProjectData(
        name = "intellijRustPlugin",
        path = perfTestProjectPath("intellij-rust"),
        openAction = ProjectOpenAction.GRADLE_PROJECT,
    )

    val actions: List<ProjectAction> = run {
        val basePath = "src/main/kotlin/org/rust"

        listOf(
            //HighlightFile("$basePath/lang/core/stubs/StubImplementations.kt"),
            HighlightFile("$basePath/lang/core/resolve/NameResolution.kt"),
            HighlightFile("$basePath/ide/annotator/RsErrorAnnotator.kt"),
            HighlightFile("$basePath/lang/core/types/infer/TypeInferenceWalker.kt"),
            HighlightFile("$basePath/lang/utils/RsDiagnostic.kt"),
            HighlightFile("$basePath/lang/core/resolve/ImplLookup.kt"),
            HighlightFile("$basePath/lang/core/macros/MacroExpansionManager.kt"),
            HighlightFile("$basePath/cargo/util/CargoOptions.kt"),
            HighlightFile("$basePath/lang/core/types/infer/TypeInference.kt"),
            HighlightFile("$basePath/lang/core/CompilerFeatures.kt"),
            HighlightFile("$basePath/lang/core/macros/ExpandedMacroStorage.kt"),
            HighlightFile("$basePath/lang/core/psi/ext/RsQualifiedNamedElement.kt"),
            HighlightFile("$basePath/ide/presentation/RsPsiRenderer.kt"),
            HighlightFile("$basePath/cargo/project/workspace/CargoWorkspace.kt"),
            HighlightFile("$basePath/cargo/project/model/impl/CargoProjectImpl.kt"),
            HighlightFile("$basePath/lang/core/resolve2/FacadeResolve.kt"),
            HighlightFile("$basePath/lang/core/resolve2/DefCollector.kt"),
            HighlightFile("$basePath/lang/core/psi/RsPsiFactory.kt"),
            HighlightFile("$basePath/lang/core/parser/RustParserUtil.kt"),
            HighlightFile("$basePath/lang/core/macros/MacroExpansionTask.kt"),
            HighlightFile("$basePath/cargo/toolchain/tools/Cargo.kt"),
        )
    }
}