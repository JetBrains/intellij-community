package org.jetbrains.tools.model.updater.impl

import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil

data class JpsLibrary(
    val name: String,
    val type: LibraryType,
    val annotations: List<JpsUrl> = emptyList(),
    val classes: List<JpsUrl> = emptyList(),
    val javadoc: List<JpsUrl> = emptyList(),
    val sources: List<JpsUrl> = emptyList(),
) {
    fun render(jpsResolverSettings: JpsResolverSettings): String {
        return xml("component", "name" to "libraryTable") {
            xml(
                "library",
                "name" to name,
                if (JpsResolverSettings.useMavenResolver && type is LibraryType.Repository) "type" to "repository" else null
            ) {
                if (JpsResolverSettings.useMavenResolver && type is LibraryType.Repository) {
                    val properties = arrayOf(
                        if (!type.includeTransitive) "include-transitive-deps" to "false" else null,
                        if (jpsResolverSettings.bindRepositoryEnabled) "jar-repository-id" to type.remoteRepository.id else null,
                        "maven-id" to type.mavenId.toString(),
                    )

                    xml("properties", *properties) {
                        if (jpsResolverSettings.sha256ChecksumsEnabled) {
                            xml("verification") {
                                for (clazz in classes) {
                                    val jpsPath = clazz.path
                                    val fileUrl = JpsUrl.File(jpsPath)
                                    xml("artifact", "url" to fileUrl.url) {
                                        xml("sha256sum") {
                                            val url = "${type.remoteRepository.url}/${jpsPath.relativePath}"
                                            val sha256checksum = KotlinTestsDependenciesUtil.sha256SumForUrl(url)
                                            raw(sha256checksum)
                                        }
                                    }
                                }
                            }
                        }

                        if (type.excludes.isNotEmpty()) {
                            xml("exclude") {
                                type.excludes.forEach { xml("dependency", "maven-id" to it.toString()) }
                            }
                        }
                    }
                }

                fun addRoots(kindName: String, roots: List<JpsUrl>) = xml(kindName) {
                    roots.forEach { xml("root", "url" to it.url) }
                }

                if (annotations.isNotEmpty()) {
                    addRoots("ANNOTATIONS", annotations)
                }
                addRoots("CLASSES", classes)
                addRoots("JAVADOC", emptyList())
                addRoots("SOURCES", sources)
            }
        }.render(addXmlDeclaration = false)
    }

    sealed class LibraryType {
        object Plain : LibraryType()

        data class Repository(
            val mavenId: MavenId,
            val excludes: List<MavenId> = emptyList(),
            val includeTransitive: Boolean = true,
            val remoteRepository: JpsRemoteRepository
        ) : LibraryType()
    }
}
