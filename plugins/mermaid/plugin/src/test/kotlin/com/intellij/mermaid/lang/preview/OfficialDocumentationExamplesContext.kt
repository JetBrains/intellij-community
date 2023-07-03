package com.intellij.mermaid.lang.preview

import org.junit.jupiter.api.extension.*
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

internal class OfficialDocumentationExamplesContext: TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    return true
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    OfficialExamplesTestData.assumeAvailable()
    val examples = OfficialExamplesTestData.testDataPath.listDirectoryEntries()
    val files = examples.flatMap { it.listDirectoryEntries() }
    val contexts = files.map { ExampleDiagramPathContext(it.nameWithoutExtension, it) }
    return (contexts as List<TestTemplateInvocationContext>).stream()
  }

  private class ExampleDiagramPathContext(
    private val name: String,
    private val path: Path
  ): TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String {
      return name
    }

    override fun getAdditionalExtensions(): List<Extension> {
      return listOf(Resolver(path))
    }

    private class Resolver(private val path: Path): ParameterResolver {
      override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == Path::class.java
      }

      override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return path
      }
    }
  }
}
