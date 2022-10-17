package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.*

open class GradleBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.GRADLE, testContext) {
  private val localGradleRepo: Path
    get() = testContext.paths.tempDir.resolve("gradle")

  fun useNewGradleLocalCache(): GradleBuildTool {
    localGradleRepo.toFile().mkdirs()
    testContext.addVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepo.toString()) }
    return this
  }

  fun removeGradleConfigFiles(): GradleBuildTool {
    logOutput("Removing Gradle config files in ${testContext.resolvedProjectHome} ...")

    testContext.resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && (it.extension == "gradle" || (it.name in listOf("gradlew", "gradlew.bat", "gradle.properties")))) {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun addPropertyToGradleProperties(property: String, value: String): GradleBuildTool {
    val projectDir = testContext.resolvedProjectHome
    val gradleProperties = projectDir.resolve("gradle.properties")
    val lineWithTheSameProperty = gradleProperties.readLines().singleOrNull { it.contains(property) }

    if (lineWithTheSameProperty != null) {
      if (lineWithTheSameProperty.contains(value)) {
        return this
      }

      val newValue = lineWithTheSameProperty.substringAfter("$property=") + " $value"
      val tempFile = File.createTempFile("newContent", ".txt").toPath()
      gradleProperties.forEachLine { line ->
        tempFile.appendText(when {
                              line.contains(property) -> "$property=$newValue" + System.getProperty("line.separator")
                              else -> line + System.getProperty("line.separator")
                            })
      }
      gradleProperties.writeText(tempFile.readText())
    }
    else {
      gradleProperties.appendLines(listOf("$property=$value"))
    }

    return this
  }

  fun setGradleJvmInProject(useJavaHomeAsGradleJvm: Boolean = true): GradleBuildTool {
    try {
      val ideaDir = testContext.resolvedProjectHome.resolve(".idea")
      val gradleXml = ideaDir.resolve("gradle.xml")

      if (gradleXml.toFile().exists()) {
        val xmlDoc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(gradleXml.toFile())
        xmlDoc.documentElement.normalize()

        val gradleSettings = xmlDoc.getElementsByTagName("GradleProjectSettings")
        if (gradleSettings.length == 1) {
          val options = (gradleSettings.item(0) as Element).getElementsByTagName("option")
          IntStream
            .range(0, options.length)
            .mapToObj { i -> options.item(i) as Element }
            .filter { it.getAttribute("name") == "gradleJvm" }
            .findAny()
            .ifPresent { node -> gradleSettings.item(0).removeChild(node) }

          if (useJavaHomeAsGradleJvm) {
            val option = xmlDoc.createElement("option")
            option.setAttribute("name", "gradleJvm")
            option.setAttribute("value", "#JAVA_HOME")
            gradleSettings.item(0).appendChild(option)
          }

          val source = DOMSource(xmlDoc)
          val outputStream = FileOutputStream(gradleXml.toFile())
          val result = StreamResult(outputStream)
          val transformerFactory = TransformerFactory.newInstance()
          val transformer = transformerFactory.newTransformer()
          transformer.transform(source, result)
          outputStream.close()
        }
      }
    }
    catch (e: Exception) {
      logError(e)
    }

    return this
  }
}