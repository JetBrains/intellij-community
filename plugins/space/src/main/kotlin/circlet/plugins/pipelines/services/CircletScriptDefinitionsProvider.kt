package circlet.plugins.pipelines.services

import circlet.pipelines.config.dsl.compile.definition.*
import klogging.*
import java.io.*
import kotlin.script.experimental.intellij.*

class CircletScriptDefinitionsProvider : ScriptDefinitionsProvider {

    companion object : KLogging()

    init {
        logger.warn("CircletScriptDefinitionsProvider ctor")
    }

    override val id: String = "CircletScriptDefinitionsProvider"

    override fun getDefinitionClasses(): Iterable<String> {
        logger.warn("getDefinitionClasses before")
        val res = listOf(DefaultProjectScriptDefinition::class.qualifiedName!!)
        logger.warn("getDefinitionClasses after")
        return res
    }

    override fun getDefinitionsClassPath(): Iterable<File> {
        // path set just for local run test. don't commit this to master
        val path = "/home/user/Documents/work/circlet2/plugins/pipelines/pipelines-config/pipelines-config-dsl-compile/build/libs/pipelines-config-dsl-compile-0.1-SNAPSHOT.jar"
        val file = File(path)
        if (!file.exists()) {
            throw Exception("File with ProjectScriptDefinition doesn't exist")
        }
        return listOf(file)
    }

    override fun useDiscovery(): Boolean {
        return true
    }
}
