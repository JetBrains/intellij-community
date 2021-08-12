package org.jetbrains.tools.model.updater.impl

data class MavenId(val groupId: String, val artifactId: String, val version: String = "") {
    val coordinates: String = if (version == "") "$groupId:$artifactId" else "$groupId:$artifactId:$version"

    companion object {
        fun fromCoordinates(coordinates: String): MavenId {
            val (group, artifact, version) = coordinates.split(":").also {
                check(it.size == 3) {
                    "mavenCoordinates ($coordinates) are expected to two semicolons"
                }
            }
            return MavenId(group, artifact, version)
        }
    }
}

fun MavenId.toJarPath(): String = "${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version.jar"
fun MavenId.toSourcesJarPath(): String = "${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version-sources.jar"
