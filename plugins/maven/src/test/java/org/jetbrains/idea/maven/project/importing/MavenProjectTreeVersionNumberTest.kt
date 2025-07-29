// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.dynatrace.hash4j.hashing.HashSink
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.idea.maven.project.MavenProjectState
import org.jetbrains.idea.maven.project.MavenProjectsTree
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.findAnnotation


class MavenProjectTreeVersionNumberTest : UsefulTestCase() {

  fun `test do not forget updating STORAGE_VERSION_NUMBER when structure changed`() {
    val hash = Hashing.komihash5_0().hashStream()
    val recursionKeeper = HashSet<String>()
    hashKType(MavenProjectState::class.createType(), recursionKeeper, hash)

    hash.putString(MavenProjectsTree.STORAGE_VERSION)
    assertEquals("UPDATE STORAGE VERSION ALONG WITH THIS HASH!!!", 5736572056086370157, hash.asLong)
  }

  private fun hashKType(type: KType, recursionKeeper: MutableSet<String>, hash: HashSink) {
    val klass = type.classifier as? KClass<*> ?: return

    hash.putString(klass.qualifiedName)
    hash.putBoolean(type.isMarkedNullable)
    type.arguments.forEach { projection ->
      val type = projection.type
      if (type == null) {
        hash.putString(projection.toString())
      }
      else {
        hashKType(type, recursionKeeper, hash)
      }
    }
    if (shouldGoDeeper(klass) && recursionKeeper.add(klass.qualifiedName!!)) {
      klass.declaredMembers.filterIsInstance<KProperty<*>>().forEach { t ->
        if (!isTransient(t)) {
          hash.putString(t.name)
          hashKType(t.returnType, recursionKeeper, hash)
        }
      }
    }
  }

  private fun shouldGoDeeper(klass: KClass<*>): Boolean {
    return klass.qualifiedName?.startsWith("org.jetbrains.idea.maven") == true
  }

  private fun isTransient(prop: KProperty<*>): Boolean {
    return prop.findAnnotation<Transient>() != null &&
           prop.findAnnotation<kotlinx.serialization.Transient>() == null
  }
}