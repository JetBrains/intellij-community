package org.jetbrains.tools.model.updater.impl

data class JpsLibrary(
    val name: String,
    val kind: Kind,
    val annotations: List<JpsUrl> = emptyList(),
    val classes: List<JpsUrl> = emptyList(),
    val sources: List<JpsUrl> = emptyList(),
) : XmlEntity {
    override fun generateXml(): String {
        val annotationsXml =
            if (annotations.isEmpty()) XmlEntity.Empty
            else XmlTag("ANNOTATIONS", children = annotations.map { XmlTag("root", attributes = listOf("url" to it.generateXml())) })
        val classesXml = XmlTag("CLASSES", children = classes.map { XmlTag("root", attributes = listOf("url" to it.generateXml())) })
        val sourcesXml = XmlTag("SOURCES", children = sources.map { XmlTag("root", attributes = listOf("url" to it.generateXml())) })
        val propertiesXml = when (kind) {
            Kind.Jars -> XmlEntity.Empty
            is Kind.Maven -> XmlTag(
                "properties",
                attributes = listOfNotNull(
                    ("include-transitive-deps" to "false").takeIf { !kind.includeTransitive },
                    "maven-id" to kind.mavenId.coordinates,
                ),
                children = listOf(
                    XmlTag(
                        "exclude",
                        children = kind.excludes.map { XmlTag("dependency", attributes = listOf("maven-id" to it.coordinates)) }
                    )
                )
            )
        }
        val typeXml = " type=\"repository\"".takeIf { kind is Kind.Maven } ?: ""
        return """
            |<component name="libraryTable">
            |  <library name="$name"$typeXml>
            |    ${propertiesXml.generateXml()}
            |    ${annotationsXml.generateXml()}
            |    ${classesXml.generateXml()}
            |    <JAVADOC />
            |    ${sourcesXml.generateXml()}
            |  </library>
            |</component>
        """.trimMarginWithInterpolations().lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    sealed class Kind {
        object Jars : Kind()
        data class Maven(val mavenId: MavenId, val excludes: List<MavenId> = emptyList(), val includeTransitive: Boolean = true) : Kind()
    }
}
