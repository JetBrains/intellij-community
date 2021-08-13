package org.jetbrains.tools.model.updater.impl

interface XmlEntity {
    fun generateXml(): String

    object Empty : XmlEntity {
        override fun generateXml(): String = ""
    }
}

sealed class JpsPath(private val root: String) : XmlEntity {
    class ProjectDir(path: String, isCommunity: Boolean) : JpsPath("\$PROJECT_DIR\$") {
        override val path: String = if (isCommunity || path.startsWith("..")) path else "community/$path"
    }

    data class MavenRepository(val mavenId: MavenId, val isSources: Boolean = false) : JpsPath("\$MAVEN_REPOSITORY\$") {
        override val path: String = if (isSources) mavenId.toSourcesJarPath() else mavenId.toJarPath()
    }

    final override fun generateXml() = "$root/$path"
    abstract val path: String
}

sealed class JpsUrl(open val jpsPath: JpsPath) : XmlEntity {
    data class File(override val jpsPath: JpsPath) : JpsUrl(jpsPath) {
        override fun generateXml(): String = "file://${jpsPath.generateXml()}"
    }

    data class Jar(override val jpsPath: JpsPath, val pathInsideJar: String = "") : JpsUrl(jpsPath) {
        override fun generateXml(): String = "jar://${jpsPath.generateXml()}!/$pathInsideJar"
    }
}

class XmlTag(
    private val tagName: String,
    private val attributes: List<Pair<String, String>> = emptyList(),
    private val children: List<XmlEntity> = emptyList()
) : XmlEntity {
    private fun isEmpty(): Boolean {
        return attributes.isEmpty() && children.all { if (it is XmlTag) it.isEmpty() else it.generateXml().isBlank() }
    }

    override fun generateXml(): String {
        val tagNameWithAttrs = listOfNotNull(
            tagName,
            attributes.takeIf { it.isNotEmpty() }?.joinToString(" ") { (key, value) -> "$key=\"$value\"" }
        ).joinToString(" ")
        return if (children.any { it is XmlTag && !it.isEmpty() }) {
            """
                |<$tagNameWithAttrs>
                |  ${children.joinToString("\n") { it.generateXml() }}
                |</$tagName>
            """.trimMarginWithInterpolations()
        } else {
            "<$tagNameWithAttrs />"
        }
    }
}
