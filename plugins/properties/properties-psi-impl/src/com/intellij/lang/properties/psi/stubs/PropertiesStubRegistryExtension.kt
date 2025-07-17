// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.stubs

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.properties.parsing.PropertiesElementTypes
import com.intellij.lang.properties.parsing.PropertiesParserDefinition
import com.intellij.lang.properties.parsing.PropertiesTokenTypes
import com.intellij.lang.properties.psi.*
import com.intellij.lang.properties.psi.impl.PropertiesListImpl
import com.intellij.lang.properties.psi.impl.PropertiesListStubImpl
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.lang.properties.psi.impl.PropertyStubImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.stubs.*
import java.io.IOException

internal class PropertiesStubRegistryExtension : StubRegistryExtension {
  override fun register(registry: StubRegistry) {
    registry.registerLightStubFactory(PropertiesElementTypes.PROPERTY_TYPE, PropertyStubFactory())
    registry.registerLightStubFactory(PropertiesElementTypes.PROPERTIES_LIST, PropertiesListStubFactory())

    registry.registerStubSerializer(PropertiesParserDefinition.FILE_ELEMENT_TYPE, DefaultFileStubSerializer())
    registry.registerStubSerializer(PropertiesElementTypes.PROPERTY_TYPE, PropertyStubSerializer())
    registry.registerStubSerializer(PropertiesElementTypes.PROPERTIES_LIST, PropertiesListStubSerializer())
  }
}

private class PropertiesListStubFactory: LightStubElementFactory<PropertiesListStub, PropertiesList> {
  override fun createStub(psi: PropertiesList, parentStub: StubElement<out PsiElement>?): PropertiesListStub {
    return PropertiesListStubImpl(parentStub)
  }

  override fun createPsi(stub: PropertiesListStub): PropertiesList {
    return PropertiesListImpl(stub)
  }

  override fun createStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): PropertiesListStub {
    return PropertiesListStubImpl(parentStub)
  }
}

private class PropertyStubFactory: LightStubElementFactory<PropertyStub, Property> {
  override fun createStub(psi: Property, parentStub: StubElement<out PsiElement>?): PropertyStub {
    return PropertyStubImpl(parentStub, psi.getKey())
  }

  override fun createPsi(stub: PropertyStub): Property? {
    return PropertyImpl(stub, PropertiesElementTypes.PROPERTY_TYPE)
  }

  override fun createStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): PropertyStub {
    val keyNode = LightTreeUtil.firstChildOfType(tree, node, PropertiesTokenTypes.KEY_CHARACTERS) ?: throw NullPointerException()
    val keyText = LightTreeUtil.toFilteredString(tree, keyNode, null)
    return PropertyStubImpl(parentStub, keyText)
  }
}

private class PropertyStubSerializer : StubSerializer<PropertyStub> {
  override fun getExternalId(): String {
    return "properties.prop"
  }

  @Throws(IOException::class)
  override fun serialize(stub: PropertyStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getKey())
  }

  @Throws(IOException::class)
  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): PropertyStub {
    return PropertyStubImpl(parentStub, dataStream.readNameString())
  }

  override fun indexStub(stub: PropertyStub, sink: IndexSink) {
    sink.occurrence(PropertyKeyIndex.KEY, PropertyImpl.unescape(stub.getKey()))
  }
}

private class PropertiesListStubSerializer : StubSerializer<PropertiesListStub> {
  override fun getExternalId(): String {
    return "properties.propertieslist"
  }

  override fun serialize(stub: PropertiesListStub, dataStream: StubOutputStream) = Unit

  @Throws(IOException::class)
  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): PropertiesListStub {
    return PropertiesListStubImpl(parentStub)
  }

  override fun indexStub(stub: PropertiesListStub, sink: IndexSink) = Unit
}