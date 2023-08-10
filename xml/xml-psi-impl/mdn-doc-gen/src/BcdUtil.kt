import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.bcd.json.Bcd
import com.intellij.bcd.json.Identifier
import java.io.File
import java.io.InputStream

fun readBcd(path: String): Bcd =
  File(path).inputStream().readBcd()

fun Bcd.resolve(path: String): Identifier =
  tryResolve(path) ?: throw RuntimeException("Failed to resolve $path")

fun Bcd.tryResolve(path: String): Identifier? {
  val segments = path.split('.', '/')
  if (segments.isEmpty()) return null
  var result = this.additionalProperties[segments[0]]
               ?: return null
  for (segment in segments.subList(1, segments.size)) {
    result = result.additionalProperties[segment]
             ?: return null
  }
  return result
}


private val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .setTypeFactory(TypeFactory.defaultInstance().withClassLoader(Bcd::class.java.classLoader))

private fun InputStream.readBcd(): Bcd =
  objectMapper.readValue(this, Bcd::class.java)