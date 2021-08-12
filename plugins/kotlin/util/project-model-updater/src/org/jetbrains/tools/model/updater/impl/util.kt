package org.jetbrains.tools.model.updater.impl

fun String.trimMarginWithInterpolations(): String {
    val regex = Regex("""^(\s*\|)(\s*).*$""")
    var prevIndent = ""
    return buildString {
        for (line in this@trimMarginWithInterpolations.lineSequence()) {
            val matchResult = regex.matchEntire(line)
            if (matchResult != null) {
                appendLine(line.removePrefix(matchResult.groupValues[1]))
                prevIndent = matchResult.groupValues[2]
            } else {
                appendLine(prevIndent + line)
            }
        }
    }
}

fun singleJarMvnLib(
    name: String,
    mavenCoordinates: String,
    excludes: List<MavenId> = emptyList(),
    transitive: Boolean = true,
    includeSources: Boolean = true,
): JpsLibrary {
    val mavenId = MavenId.fromCoordinates(mavenCoordinates)
    return JpsLibrary(
        name,
        JpsLibrary.Kind.Maven(mavenId, includeTransitive = transitive, excludes = excludes),
        classes = listOf(JpsUrl.Jar(JpsPath.MavenRepository(mavenId))),
        sources = listOf(JpsUrl.Jar(JpsPath.MavenRepository(mavenId, isSources = true))).takeIf { includeSources } ?: emptyList()
    )
}

fun String.escapeForRegex() = replace("/", "\\/")
    .replace("$", "\\$")
    .replace(".", "\\.")
