// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.projects

import org.jetbrains.kotlin.idea.project.test.base.ProjectData
import org.jetbrains.kotlin.idea.project.test.base.actions.HighlightFileAction
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.perfTestProjectPath
import org.jetbrains.kotlin.idea.performance.tests.utils.project.ProjectOpenAction

object KotlinProject {
    val project = ProjectData(
      id = "kotlin",
      path = perfTestProjectPath("kotlin"),
      openAction = ProjectOpenAction.GRADLE_PROJECT,
    )

    val actions: List<ProjectAction> =
        listOf(
            HighlightFileAction("analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtFirDiagnosticsImpl.kt"),
            HighlightFileAction("analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/diagnostics/KtFirDataClassConverters.kt"),
            HighlightFileAction("compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/diagnostics/FirErrorsDefaultMessages.kt"),
            HighlightFileAction("compiler/fir/raw-fir/light-tree2fir/src/org/jetbrains/kotlin/fir/lightTree/converter/DeclarationsConverter.kt"),
            HighlightFileAction("compiler/backend/src/org/jetbrains/kotlin/codegen/state/KotlinTypeMapper.kt"),
            HighlightFileAction("compiler/frontend/cfg/src/org/jetbrains/kotlin/cfg/ControlFlowProcessor.kt"),
            HighlightFileAction("compiler/fir/checkers/checkers-component-generator/src/org/jetbrains/kotlin/fir/checkers/generator/diagnostics/FirDiagnosticsList.kt"),
            HighlightFileAction("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/dfa/FirDataFlowAnalyzer.kt"),
            HighlightFileAction("compiler/ir/backend.jvm/codegen/src/org/jetbrains/kotlin/backend/jvm/codegen/ExpressionCodegen.kt"),
            HighlightFileAction("plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/stubs/ClassFileToSourceStubConverter.kt"),
            HighlightFileAction("compiler/ir/serialization.common/src/org/jetbrains/kotlin/backend/common/serialization/IrFileSerializer.kt"),
            HighlightFileAction("compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrDeclarationStorage.kt"),
            HighlightFileAction("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/dfa/cfg/ControlFlowGraphBuilder.kt"),
            HighlightFileAction("compiler/daemon/daemon-tests/test/org/jetbrains/kotlin/daemon/experimental/integration/CompilerDaemonTest.kt"),
            HighlightFileAction("compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/LightTreePositioningStrategies.kt"),
            HighlightFileAction("compiler/frontend/cfg/src/org/jetbrains/kotlin/cfg/ControlFlowInformationProviderImpl.kt"),
            HighlightFileAction("compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt"),
            HighlightFileAction("compiler/fir/raw-fir/light-tree2fir/src/org/jetbrains/kotlin/fir/lightTree/converter/ExpressionsConverter.kt"),
            HighlightFileAction("compiler/fir/dump/src/org/jetbrains/kotlin/fir/dump/HtmlFirDump.kt"),
            HighlightFileAction("plugins/kotlin-serialization/kotlin-serialization-compiler/src/org/jetbrains/kotlinx/serialization/compiler/backend/ir/GeneratorHelpers.kt"),
            HighlightFileAction("compiler/frontend/src/org/jetbrains/kotlin/resolve/constants/evaluate/ConstantExpressionEvaluator.kt"),
        )

}