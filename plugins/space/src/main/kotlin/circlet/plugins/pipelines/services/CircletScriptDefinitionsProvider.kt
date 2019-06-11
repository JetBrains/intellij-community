package circlet.plugins.pipelines.services

import circlet.pipelines.config.dsl.scriptdefinition.*
import circlet.plugins.pipelines.utils.*
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
        val res = listOf(ProjectScriptDefinition::class.qualifiedName!!)
        logger.warn("getDefinitionClasses after")
        return res
    }

    override fun getDefinitionsClassPath(): Iterable<File> {
        // path set just for local run test. don't commit this to master
        val url = find(CircletScriptDefinitionsProvider::class, "pipelines-config-dsl-scriptdefinition")
        val file = File(url.file)
        if (!file.exists()) {
            throw Exception("File with ProjectScriptDefinition doesn't exist")
        }
        return listOf(file)
    }

    override fun useDiscovery(): Boolean {
        return true
    }
}
