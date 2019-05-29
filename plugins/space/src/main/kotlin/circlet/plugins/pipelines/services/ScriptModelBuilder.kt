package circlet.plugins.pipelines.services

import circlet.pipelines.config.dsl.compile.*
import circlet.pipelines.config.dsl.compile.util.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.openapi.project.*
import runtime.reactive.*
import java.io.*

class ScriptModelBuilder {
    suspend fun build(lifetime: Lifetime, project: Project): ScriptViewModel {

        val basePath = project.basePath
        if (basePath == null) {
            return createEmptyScriptViewModel(lifetime)
        }

        val expectedFile = File(basePath, "Circlet.kts")
        if (!expectedFile.exists())
        {
            return createEmptyScriptViewModel(lifetime)
        }

        val kotlinCompilerPath = KotlinCompilerFinder().find()
        val dslJarPath = findDslJarPath()

        val targetJar = createTempDir().absolutePath + "/compiledJar.jar"
        val sourceCodeResolver = LocalSourceCodeResolver()
        DslJarCompiler().compile(expectedFile.absolutePath, targetJar, sourceCodeResolver, kotlinCompilerPath, dslJarPath)

        val config = DslScriptExecutor().evaluateModel(targetJar, "", "", "")

        return ScriptViewModel(lifetime, config)
    }
}
