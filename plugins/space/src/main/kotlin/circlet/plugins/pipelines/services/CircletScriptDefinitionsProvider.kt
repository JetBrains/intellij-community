package circlet.plugins.pipelines.services

import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.utils.*
import java.io.*
import kotlin.script.experimental.intellij.*

class CircletScriptDefinitionsProvider : ScriptDefinitionsProvider {

    override val id: String = "CircletScriptDefinitionsProvider"

    override fun getDefinitionClasses(): Iterable<String> {
        return listOf(ScriptConstants.ScriptTemplateClassQualifiedName)
    }

    override fun getDefinitionsClassPath(): Iterable<File> {
        val defFile = JarFinder.find("pipelines-config-scriptdefinition-compile")
        if (!defFile.exists()) {
            throw Exception("File with ProjectScriptDefinition doesn't exist")
        }

        return listOf(defFile)
    }

    override fun useDiscovery(): Boolean {
        return true
    }

}
