// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.project.test.projects

import org.jetbrains.kotlin.idea.perf.common.HighlightFile
import org.jetbrains.kotlin.idea.perf.common.ProjectAction
import org.jetbrains.kotlin.idea.perf.common.ProjectData
import org.jetbrains.kotlin.idea.perf.common.perfTestProjectPath
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

object KotlinProject {
    val project = ProjectData(
        name = "kotlin",
        path = perfTestProjectPath("kotlin"),
        openAction = ProjectOpenAction.GRADLE_PROJECT,
    )

    val actions: List<ProjectAction> =
        listOf(
            HighlightFile("analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtFirDiagnosticsImpl.kt"),
            HighlightFile("analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtFirDataClassConverters.kt"),
            HighlightFile("compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/diagnostics/FirErrorsDefaultMessages.kt"),
            HighlightFile("compiler/fir/raw-fir/light-tree2fir/src/org/jetbrains/kotlin/fir/lightTree/converter/DeclarationsConverter.kt"),
            HighlightFile("compiler/backend/src/org/jetbrains/kotlin/codegen/state/KotlinTypeMapper.kt"),
            HighlightFile("compiler/frontend/cfg/src/org/jetbrains/kotlin/cfg/ControlFlowProcessor.kt"),
            HighlightFile("compiler/fir/checkers/checkers-component-generator/src/org/jetbrains/kotlin/fir/checkers/generator/diagnostics/FirDiagnosticsList.kt"),
            HighlightFile("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/dfa/FirDataFlowAnalyzer.kt"),
            HighlightFile("compiler/ir/backend.jvm/codegen/src/org/jetbrains/kotlin/backend/jvm/codegen/ExpressionCodegen.kt"),
            HighlightFile("plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/stubs/ClassFileToSourceStubConverter.kt"),
            HighlightFile("compiler/ir/serialization.common/src/org/jetbrains/kotlin/backend/common/serialization/IrFileSerializer.kt"),
            HighlightFile("compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrDeclarationStorage.kt"),
            HighlightFile("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/dfa/cfg/ControlFlowGraphBuilder.kt"),
            HighlightFile("compiler/daemon/daemon-tests/test/org/jetbrains/kotlin/daemon/experimental/integration/CompilerDaemonTest.kt"),
            HighlightFile("compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/LightTreePositioningStrategies.kt"),
            HighlightFile("compiler/frontend/cfg/src/org/jetbrains/kotlin/cfg/ControlFlowInformationProviderImpl.kt"),
            HighlightFile("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt"),
            HighlightFile("compiler/fir/raw-fir/light-tree2fir/src/org/jetbrains/kotlin/fir/lightTree/converter/ExpressionsConverter.kt"),
            HighlightFile("compiler/fir/dump/src/org/jetbrains/kotlin/fir/dump/HtmlFirDump.kt"),
            HighlightFile("plugins/kotlin-serialization/kotlin-serialization-compiler/src/org/jetbrains/kotlinx/serialization/compiler/backend/ir/GeneratorHelpers.kt"),
            HighlightFile("compiler/frontend/src/org/jetbrains/kotlin/resolve/constants/evaluate/ConstantExpressionEvaluator.kt"),
        )

}