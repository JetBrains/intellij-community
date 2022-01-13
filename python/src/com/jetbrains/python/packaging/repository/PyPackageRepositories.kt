// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus

@State(name="PyPackageRepositories",
       storages = [Storage("python-repositories.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
@ApiStatus.Experimental
class PyPackageRepositories : PersistentStateComponent<PyPackageRepositories> {
  @Property
  val repositories = mutableListOf<PyPackageRepository>()

  @Property
  val invalidRepositories = mutableSetOf<PyPackageRepository>()

  val validRepositories: List<PyPackageRepository>
    get() = repositories.filterNot { it in invalidRepositories }


  override fun getState(): PyPackageRepositories {
    return this
  }

  override fun loadState(state: PyPackageRepositories) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun findByUrl(url: String): PyPackageRepository? {
    return repositories.find { url == it.repositoryUrl }
  }

  fun markInvalid(url: String) {
    findByUrl(url)?.let(invalidRepositories::add)
  }
}