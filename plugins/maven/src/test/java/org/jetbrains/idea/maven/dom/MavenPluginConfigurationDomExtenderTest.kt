// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.ResourceUtil
import com.intellij.util.xml.Converter
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.Required
import com.intellij.util.xml.XmlName
import com.intellij.util.xml.reflect.CustomDomChildrenDescription
import com.intellij.util.xml.reflect.DomExtender
import com.intellij.util.xml.reflect.DomExtension
import com.intellij.util.xml.reflect.DomExtensionsRegistrar
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel
import org.junit.Test
import java.lang.reflect.Type
import java.util.function.Supplier

class MavenPluginConfigurationDomExtenderTest : MavenDomTestCase() {

  @Test
  fun testShouldCorrectlyAddTagsForMaven3Plugin() = runBlocking {
    assumeMaven3()
    doTestWith(pluginModelFileName = "compiler-3-14-1.xml")
  }

  @Test
  fun testShouldCorrectlyAddTagsForMaven4Plugin() = runBlocking {
    assumeMaven4()
    doTestWith(pluginModelFileName = "compiler-4-0-beta3.xml")
  }

  private suspend fun doTestWith(pluginModelFileName: String) {
    importProjectAsync("""
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1</version>
        <build>
          <plugins>
              <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                  <configuration>
                      <compilerArgs>
                          <arg>-some-arg</arg>
                      </compilerArgs>
                  </configuration>
              </plugin>
          </plugins>
        </build>
  """
    )

    val pluginFile = createFromFile(pluginModelFileName, "plugin-test/$pluginModelFileName")
    readAction {
      val dom = MavenDomUtil.getMavenDomProjectModel(project, projectPom)!!
      val compilerConfiguration = dom
        .build
        .plugins
        .plugins
        .single { it.artifactId.stringValue == "maven-compiler-plugin" }
        .configuration

      val pluginFileModel = MavenDomUtil.getMavenDomModel(project, pluginFile, MavenDomPluginModel::class.java)
      val extender = MavenPluginConfigurationDomExtender {
        pluginFileModel
      }
      val registeredExtensions = ArrayList<MyDomExtention>()
      val registrar = MyRegistrar(registeredExtensions)

      extender.registerExtensions(compilerConfiguration, registrar)
      val basedir2ForTest = registeredExtensions.singleOrNull() { it.xmlName.localName == "basedir2ForTest" }
      assertNotNull(basedir2ForTest)
      assertEquals(1, basedir2ForTest!!.customAnnotations.size)
      assertTrue(basedir2ForTest.customAnnotations[0] is Required)

      val basedir = registeredExtensions.singleOrNull() { it.xmlName.localName == "basedir" }
      assertNotNull(basedir)
      assertEmpty(basedir!!.customAnnotations)
    }
  }

  private fun createFromFile(file: String, resourceName: String): VirtualFile {
    val result = ResourceUtil.getResourceAsStream(this::class.java.classLoader, "org/jetbrains/maven/", resourceName).use {
      StreamUtil.readBytes(it).toString(Charsets.UTF_8)
    }
    return createProjectSubFile(file, result)
  }

}


private class MyDomExtention(val xmlName: XmlName, private val type: Type) : DomExtension {
  var declaringElement: DomElement? = null
  var declaringElementFinder: Supplier<out DomElement?>? = null
  var declaringPsiElement: PsiElement? = null
  var converter: Converter<*>? = null
  var isSoft: Boolean = false
  val customAnnotations: MutableList<Annotation> = ArrayList()
  val userData: MutableMap<Key<*>, Any?> = HashMap()
  val extenders: MutableList<DomExtender<*>> = ArrayList()

  override fun getType(): Type {
    return type
  }

  override fun setDeclaringElement(declaringElement: DomElement): DomExtension {
    this.declaringElement = declaringElement
    return this
  }

  override fun setDeclaringDomElement(declarationFinder: Supplier<out DomElement?>): DomExtension {
    this.declaringElementFinder = declarationFinder
    return this
  }

  override fun setDeclaringElement(declaringElement: PsiElement): DomExtension {
    this.declaringPsiElement = declaringElement
    return this
  }

  override fun setConverter(converter: Converter<*>): DomExtension {
    this.converter = converter
    return this
  }

  override fun setConverter(converter: Converter<*>, soft: Boolean): DomExtension {
    this.converter = converter
    this.isSoft = soft
    return this
  }

  override fun addCustomAnnotation(anno: Annotation): DomExtension {
    customAnnotations.add(anno)
    return this
  }

  override fun <T : Any?> putUserData(key: Key<T?>?, value: T?) {
    if (key != null) {
      @Suppress("UNCHECKED_CAST")
      (userData as MutableMap<Key<T?>, T?>)[key] = value
    }
  }

  override fun addExtender(extender: DomExtender<*>?): DomExtension {
    if (extender != null) {
      extenders.add(extender)
    }
    return this
  }

}

private class MyRegistrar(val registeredExtensions: MutableList<MyDomExtention>) : DomExtensionsRegistrar {
  override fun registerFixedNumberChildExtension(name: XmlName, type: Type): DomExtension {
    return MyDomExtention(name, type).also { registeredExtensions.add(it) }
  }

  override fun registerCollectionChildrenExtension(name: XmlName, type: Type): DomExtension {
    return MyDomExtention(name, type).also { registeredExtensions.add(it) }
  }

  override fun registerGenericAttributeValueChildExtension(name: XmlName, parameterType: Type?): DomExtension {
    TODO()
  }

  override fun registerAttributeChildExtension(name: XmlName, type: Type): DomExtension {
    TODO()
  }

  override fun registerCustomChildrenExtension(type: Type): DomExtension {
    TODO()
  }

  override fun registerCustomChildrenExtension(type: Type, descriptor: CustomDomChildrenDescription.TagNameDescriptor): DomExtension {
    TODO()
  }

  override fun registerCustomChildrenExtension(type: Type, attributeDescriptor: CustomDomChildrenDescription.AttributeDescriptor): DomExtension {
    TODO()
  }

}


