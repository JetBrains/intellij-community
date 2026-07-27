package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.bundles.BundleType
import org.jetbrains.plugins.textmate.bundles.TextMateNioResourceReader
import org.jetbrains.plugins.textmate.bundles.readSublimeBundle
import org.jetbrains.plugins.textmate.bundles.readTextMateBundle
import org.jetbrains.plugins.textmate.bundles.readVSCBundle
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.plist.JsonOrXmlOrYamlPlistReader
import org.jetbrains.plugins.textmate.plist.XmlPlistReaderForTests
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Estimates the retained heap footprint of [TextMateSyntaxTableCore] built from all bundles
 * shipped with the TextMate plugin (`plugins/textmate/lib/bundles`).
 *
 * The estimate is produced by a reflective object-graph walk using the standard HotSpot layout
 * assumptions (64-bit, compressed oops): 12-byte object headers, 16-byte array headers,
 * 4-byte references, 8-byte alignment. Absolute numbers are approximations,
 * but they are stable and directly comparable between runs, which is what matters
 * for tracking footprint regressions and improvements.
 */
class TextMateSyntaxTableMemoryTest {
  @Test
  fun estimateSyntaxTableFootprint() {
    val bundlesDir = Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/lib/bundles")
    assertTrue(bundlesDir.isDirectory(), "Cannot find bundled TextMate bundles at $bundlesDir")

    val interner = TextMateConcurrentMapInterner()
    val builder = TextMateSyntaxTableBuilder(interner)
    var bundleCount = 0
    var grammarCount = 0
    bundlesDir.listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name }.forEach { bundleDir ->
      val resourceReader = TextMateNioResourceReader(bundleDir)
      val plistReader = JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReaderForTests())
      val bundleReader = when (BundleType.detectBundleType(resourceReader, bundleDir.name)) {
        BundleType.TEXTMATE -> readTextMateBundle(bundleDir.name, plistReader, resourceReader)
        BundleType.SUBLIME -> readSublimeBundle(bundleDir.name, plistReader, resourceReader)
        BundleType.VSCODE -> readVSCBundle(plistReader, resourceReader) ?: error("Cannot read VSC bundle: ${bundleDir.name}")
        BundleType.UNDEFINED -> error("Unknown bundle type: ${bundleDir.name}")
      }
      bundleCount++
      for (grammar in bundleReader.readGrammars()) {
        // grammars without a scope name are skipped by production code as well, see TextMateServiceImpl.registerBundle
        if (builder.addSyntax(grammar.plist.value) != null) {
          grammarCount++
        }
      }
    }
    assertTrue(bundleCount > 0, "No bundles were loaded from $bundlesDir")

    val table = builder.build()
    val footprint = ObjectGraphMeasurer.measure(table)

    val report = buildString {
      appendLine("TextMateSyntaxTableCore footprint ($bundleCount bundles, $grammarCount grammars):")
      appendLine("  total: ${footprint.totalBytes} bytes (%.2f MB) in ${footprint.totalObjects} objects".format(footprint.totalBytes / 1024.0 / 1024.0))
      appendLine("  by class:")
      footprint.histogram.entries
        .sortedByDescending { it.value.bytes }
        .forEach { (className, stat) ->
          appendLine("    %,12d bytes %,9d objects  %s".format(stat.bytes, stat.count, className))
        }
    }
    println(report)

    assertTrue(footprint.totalBytes > 0)
  }
}

private class ClassStat {
  var count: Long = 0
  var bytes: Long = 0
}

private class Footprint(
  val totalBytes: Long,
  val totalObjects: Long,
  val histogram: Map<String, ClassStat>,
)

/**
 * Walks an object graph reflectively and estimates its retained size.
 *
 * Shared singletons that don't really belong to the graph (enum constants and `Class` objects)
 * are not counted. Every other object is counted exactly once, no matter how many references
 * point to it.
 */
private object ObjectGraphMeasurer {
  private const val OBJECT_HEADER = 12L
  private const val ARRAY_HEADER = 16L
  private const val REFERENCE_SIZE = 4L

  private class ClassLayout(val shallowSize: Long, val referenceFields: List<Field>)

  private val layouts = HashMap<Class<*>, ClassLayout>()

  fun measure(root: Any): Footprint {
    val visited = IdentityHashMap<Any, Boolean>()
    val histogram = HashMap<String, ClassStat>()
    var totalBytes = 0L
    var totalObjects = 0L
    val queue = ArrayDeque<Any>()
    queue.addLast(root)
    visited[root] = true
    while (queue.isNotEmpty()) {
      val obj = queue.removeLast()
      val clazz = obj.javaClass
      val shallowSize: Long
      if (clazz.isArray) {
        val length = java.lang.reflect.Array.getLength(obj)
        val componentType = clazz.componentType
        shallowSize = align(ARRAY_HEADER + length * primitiveSize(componentType))
        if (!componentType.isPrimitive) {
          @Suppress("UNCHECKED_CAST")
          for (element in obj as Array<Any?>) {
            enqueue(element, visited, queue)
          }
        }
      }
      else {
        val layout = layoutOf(clazz)
        shallowSize = layout.shallowSize
        for (field in layout.referenceFields) {
          enqueue(field.get(obj), visited, queue)
        }
      }
      totalBytes += shallowSize
      totalObjects++
      histogram.getOrPut(clazz.typeName) { ClassStat() }.let {
        it.count++
        it.bytes += shallowSize
      }
    }
    return Footprint(totalBytes, totalObjects, histogram)
  }

  private fun enqueue(element: Any?, visited: IdentityHashMap<Any, Boolean>, queue: ArrayDeque<Any>) {
    if (element == null || element is Class<*> || element is Enum<*>) return
    if (visited.put(element, true) == null) {
      queue.addLast(element)
    }
  }

  private fun layoutOf(clazz: Class<*>): ClassLayout {
    return layouts.getOrPut(clazz) {
      var fieldsSize = 0L
      val referenceFields = mutableListOf<Field>()
      var c: Class<*>? = clazz
      while (c != null) {
        for (field in c.declaredFields) {
          if (Modifier.isStatic(field.modifiers)) continue
          val type = field.type
          if (type.isPrimitive) {
            fieldsSize += primitiveSize(type)
          }
          else {
            fieldsSize += REFERENCE_SIZE
            field.isAccessible = true
            referenceFields.add(field)
          }
        }
        c = c.superclass
      }
      ClassLayout(align(OBJECT_HEADER + fieldsSize), referenceFields)
    }
  }

  private fun primitiveSize(type: Class<*>): Long {
    return when (type) {
      java.lang.Boolean.TYPE, java.lang.Byte.TYPE -> 1L
      java.lang.Character.TYPE, java.lang.Short.TYPE -> 2L
      Integer.TYPE, java.lang.Float.TYPE -> 4L
      java.lang.Long.TYPE, java.lang.Double.TYPE -> 8L
      else -> REFERENCE_SIZE
    }
  }

  private fun align(size: Long): Long = (size + 7) and 7L.inv()
}
