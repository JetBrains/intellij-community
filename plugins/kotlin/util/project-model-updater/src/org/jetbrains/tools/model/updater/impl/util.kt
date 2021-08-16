package org.jetbrains.tools.model.updater.impl

import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.ByteArrayOutputStream
import java.io.File

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

fun File.readXml(): Element {
    return inputStream().use { SAXBuilder().build(it).rootElement }
}

fun String.readXml(): Element {
    return byteInputStream().use { SAXBuilder().build(it).rootElement }
}

fun String.jpsEntityNameToFilename(): String = replace(".", "_").replace("-", "_").replace(" ", "_")

fun Element.writeXml(): String {
    val bos = ByteArrayOutputStream()

    bos.writer().use { writer ->
        val format = Format.getCompactFormat()
            .setIndent("  ")
            .setTextMode(Format.TextMode.TRIM)
            .setEncoding("UTF-8")
            .setOmitEncoding(false)
            .setOmitDeclaration(false)
            .setLineSeparator("\n")

        val outputter = XMLOutputter(format)
        outputter.output(this, writer)
    }

    return bos.toString()
}

private suspend fun SequenceScope<Element>.visit(element: Element) {
    element.children.forEach { visit(it) }
    yield(element)
}
fun Element.traverseChildren(): Sequence<Element> {
    return sequence { visit(this@traverseChildren) }
}
