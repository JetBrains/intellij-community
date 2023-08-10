package org.jetbrains.tools.model.updater.impl

data class MavenId(val groupId: String, val artifactId: String, val version: String? = null) {
    override fun toString(): String = buildString {
        append(groupId).append(':').append(artifactId)
        if (version != null) {
            append(':').append(version)
        }
    }

    fun toJarPath(classifier: String?): String {
        val classifierSuffix = if (classifier != null) "-$classifier" else ""
        return "${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version$classifierSuffix.jar"
    }

    companion object {
        fun parse(coordinates: String): MavenId {
            val (group, artifact, version) = coordinates.split(":").also {
                check(it.size == 3) {
                    "Invalid Maven coordinates ($coordinates), expected \"groupId:artifactId:version\""
                }
            }
            return MavenId(group, artifact, version)
        }
    }
}