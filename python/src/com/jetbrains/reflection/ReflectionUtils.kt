// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.reflection

import com.intellij.util.containers.HashMap
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.ref.SoftReference
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType
import kotlin.reflect.full.memberProperties

/**
 * Tools to fetch properties both from Java and Kotlin code and to copy them from one object to another.
 * To be a property container class should have kotlin properties, java bean properties or implement [SimplePropertiesProvider].
 *
 * Properties should be writable except [DelegationProperty]
 * @author Ilya.Kazakevich
 */

interface Property {
  fun getName(): String
  fun getType(): java.lang.reflect.Type
  fun get(): Any?
  fun set(value: Any?)
}

private class KotlinProperty(val property: KMutableProperty<*>, val instance: Any?) : Property {
  override fun getName() = property.name
  override fun getType() = property.returnType.javaType
  override fun get() = property.getter.call(instance)
  override fun set(value: Any?) = property.setter.call(instance, value)
}

private class JavaProperty(val property: PropertyDescriptor, val instance: Any?) : Property {
  override fun getName() = property.name!!
  override fun getType() = property.propertyType!!
  override fun get() = property.readMethod.invoke(instance)!!
  override fun set(value: Any?) {
    property.writeMethod.invoke(instance, value)
  }
}

private class SimpleProperty(private val propertyName: String,
                             private val provider: SimplePropertiesProvider) : Property {
  override fun getName() = propertyName
  override fun getType() = String::class.java
  override fun get() = provider.getPropertyValue(propertyName)
  override fun set(value: Any?) = provider.setPropertyValue(propertyName, value?.toString())
}

/**
 * Implement to handle properties manually
 */
interface SimplePropertiesProvider {
  val propertyNames: List<String>
  fun setPropertyValue(propertyName: String, propertyValue: String?)
  fun getPropertyValue(propertyName: String): String?
}

class Properties(val properties: List<Property>, val instance: Any) {
  val propertiesMap: MutableMap<String, Property> = HashMap(properties.map { Pair(it.getName(), it) }.toMap())

  init {
    if (instance is SimplePropertiesProvider) {
      instance.propertyNames.forEach { propertiesMap.put(it, SimpleProperty(it, instance)) }
    }
  }

  fun copyTo(dst: Properties) {
    propertiesMap.values.forEach {
      val dstProperty = dst.propertiesMap[it.getName()]
      if (dstProperty != null) {
        val value = it.get()
        dstProperty.set(value)
      }
    }
  }
}


private fun KProperty<*>.isAnnotated(annotation: KClass<*>): Boolean {
  return this.annotations.find { annotation.java.isAssignableFrom(it.javaClass) } != null
}


private val membersCache: MutableMap<KClass<*>, SoftReference<Collection<KProperty<*>>>> = com.intellij.util.containers.ContainerUtil.createSoftMap()

private fun KClass<*>.memberPropertiesCached(): Collection<KProperty<*>> {
  synchronized(membersCache) {
    val cache = membersCache[this]?.get()
    if (cache != null) {
      return cache
    }
    val memberProperties = this.memberProperties
    membersCache.put(this, SoftReference(memberProperties))
    return memberProperties
  }
}

/**
 * @param instance object with properties (see module doc)
 * @param annotationToFilterByClass optional annotation class to fetch only kotlin properties annotated with it. Only supported in Kotlin
 * @param usePojoProperties search for java-style properties (kotlin otherwise)
 * @return properties of some object
 */
fun getProperties(instance: Any, annotationToFilterByClass: Class<*>? = null, usePojoProperties: Boolean = false): Properties {
  val annotationToFilterBy = annotationToFilterByClass?.kotlin

  if (usePojoProperties) {
    // Java props
    val javaProperties = Introspector.getBeanInfo(instance.javaClass).propertyDescriptors
    assert(annotationToFilterBy == null, { "Filtering java properties is not supported" })
    return Properties(javaProperties.map { JavaProperty(it, instance) }, instance)
  }
  else {
    // Kotlin props
    val klass = instance.javaClass.kotlin
    val allKotlinProperties = LinkedHashSet(klass.memberPropertiesCached().filterIsInstance(KProperty::class.java))

    val delegatedProperties = ArrayList<Property>() // See DelegationProperty doc
    allKotlinProperties.filter { it.isAnnotated(DelegationProperty::class) }.forEach {
      val delegatedInstance = it.getter.call(instance)
      if (delegatedInstance != null) {
        delegatedProperties.addAll(getProperties(delegatedInstance, annotationToFilterBy?.java, false).properties)
        allKotlinProperties.remove(it)
      }
    }
    val firstLevelProperties = allKotlinProperties.filterIsInstance(KMutableProperty::class.java)

    if (annotationToFilterBy == null) {
      return Properties(firstLevelProperties.map { KotlinProperty(it, instance) } + delegatedProperties, instance)
    }
    return Properties(
      firstLevelProperties.filter { it.isAnnotated(annotationToFilterBy) }.map { KotlinProperty(it, instance) } + delegatedProperties, instance)
  }
}


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * Property marked with it is not considered to be [Property] by itself, but class with properties instead.
 * Following structure is example:
 * class User:
 *  +familyName: String
 *  +lastName: String
 *  +credentials: Credentials
 *
 * class Credentials:
 *  +login: String
 *  +password: String
 *
 * Property credentials here is [DelegationProperty]. It can be val, but all other properties should be var
 */
annotation class DelegationProperty