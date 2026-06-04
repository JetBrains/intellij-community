package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.test.OfficialDocumentationExamples
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream

internal class OfficialDocumentationExamplesContext: TestTemplateInvocationContextProvider {
  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    return true
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
    assumeAvailable()
    val examples = obtainExamples()
    val files = examples.values.asSequence().flatten()
    val contexts = files.map { ExampleDiagramPathContext(it.nameWithoutExtension, it) }
    return (contexts.toList() as List<TestTemplateInvocationContext>).stream()
  }

  private fun assumeAvailable() {
    Assumptions.assumeTrue({ obtainBasePath() != null }, "Could not obtain base data path")
  }

  private fun obtainExamples(): Map<String, List<VirtualFile>> {
    val base = obtainBasePath()
    checkNotNull(base) { "Failed to obtain base data path" }
    val diagrams = base.children
    return buildMap {
      for (diagram in diagrams) {
        put(diagram.name, diagram.children.toList())
      }
    }
  }

  private fun obtainBasePath(): VirtualFile? {
    val basePath = OfficialDocumentationExamples.obtainBasePath()
    return VfsUtil.findFileByURL(basePath)
  }

  private class ExampleDiagramPathContext(
    private val name: String,
    private val path: VirtualFile
  ): TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String {
      return name
    }

    override fun getAdditionalExtensions(): List<Extension> {
      return listOf(Resolver(path))
    }

    private class Resolver(private val path: VirtualFile): ParameterResolver {
      override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == VirtualFile::class.java
      }

      override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return path
      }
    }
  }
}
