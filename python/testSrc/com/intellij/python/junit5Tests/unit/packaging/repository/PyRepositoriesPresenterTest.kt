// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.repository

import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PyRepositoriesPresenter
import com.jetbrains.python.packaging.repository.RepositoryTreeSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyRepositoriesPresenterTest {

  private fun newService(repos: List<PyPackageRepository> = emptyList()): PyPackageRepositories {
    val service = PyPackageRepositories()
    service.repositories.addAll(repos)
    return service
  }

  private fun repo(name: String, url: String = "https://$name.example.com/simple"): PyPackageRepository =
    PyPackageRepository(name, url, null)

  @Test
  fun `loadCustomRepositories returns a defensive copy`() {
    val original = repo("A")
    val service = newService(listOf(original))
    val presenter = PyRepositoriesPresenter(service)

    val snapshot = presenter.loadCustomRepositories()
    service.repositories.add(repo("B"))

    assertEquals(listOf(original), snapshot)
  }

  @Test
  fun `commitCustomRepositories replaces the persisted list`() {
    val service = newService(listOf(repo("old1"), repo("old2")))
    val presenter = PyRepositoriesPresenter(service)

    val newList = listOf(repo("new1"), repo("new2"), repo("new3"))
    presenter.commitCustomRepositories(newList)

    assertEquals(newList, service.repositories.toList())
  }

  @Test
  fun `findRemoved returns persisted repositories absent from remaining`() {
    val keep = repo("keep")
    val drop = repo("drop")
    val presenter = PyRepositoriesPresenter(newService(listOf(keep, drop)))

    val removed = presenter.findRemoved(listOf(keep))

    assertEquals(listOf(drop), removed)
  }

  @Test
  fun `findRemoved returns empty when nothing was removed`() {
    val a = repo("a")
    val b = repo("b")
    val presenter = PyRepositoriesPresenter(newService(listOf(a, b)))

    assertTrue(presenter.findRemoved(listOf(a, b)).isEmpty())
  }

  @Test
  fun `nextUniqueName returns a name not in the given list`() {
    val presenter = PyRepositoriesPresenter(newService())

    val suggestion = presenter.nextUniqueName(listOf("Custom", "Custom (1)"))

    assertTrue(suggestion !in listOf("Custom", "Custom (1)"),
               "Suggested name '$suggestion' must not collide with existing names")
  }

  @Test
  fun `nextUniqueName returns distinct values when called twice with the first result`() {
    val presenter = PyRepositoriesPresenter(newService())

    val first = presenter.nextUniqueName(emptyList())
    val second = presenter.nextUniqueName(listOf(first))

    assertNotEquals(first, second)
  }

  @Test
  fun `createDraftRepository builds a repository with a unique name and empty url`() {
    val presenter = PyRepositoriesPresenter(newService())
    val existing = listOf("Custom")

    val draft = presenter.createDraftRepository(existing)

    assertTrue(draft.name !in existing)
    assertEquals("", draft.repositoryUrl)
    assertEquals(null, draft.login)
  }

  @Test
  fun `isPersisted returns true for repositories present in the service`() {
    val stored = repo("kept")
    val presenter = PyRepositoriesPresenter(newService(listOf(stored)))

    assertTrue(presenter.isPersisted(stored))
  }

  @Test
  fun `isPersisted returns false for repositories absent from the service`() {
    val presenter = PyRepositoriesPresenter(newService(listOf(repo("only-this"))))

    assertEquals(false, presenter.isPersisted(repo("other")))
  }

  @Test
  fun `rebuildTree emits clear then each persisted repository then defaults`() {
    val a = repo("a")
    val b = repo("b")
    val presenter = PyRepositoriesPresenter(newService(listOf(a, b)))
    val events = mutableListOf<String>()
    val sink = object : RepositoryTreeSink {
      override fun removeAllRepositoryNodes() {
        events += "clear"
      }
      override fun addCustomRepositoryNode(repo: PyPackageRepository) {
        events += "addCustom:${repo.name}"
      }
      override fun reinstateDefaultRepositoryNodes() {
        events += "addDefaults"
      }
    }

    presenter.rebuildTree(sink)

    assertEquals(listOf("clear", "addCustom:a", "addCustom:b", "addDefaults"), events)
  }

  @Test
  fun `rebuildTree on empty service still clears and re-adds defaults`() {
    val presenter = PyRepositoriesPresenter(newService())
    val events = mutableListOf<String>()
    val sink = object : RepositoryTreeSink {
      override fun removeAllRepositoryNodes() {
        events += "clear"
      }
      override fun addCustomRepositoryNode(repo: PyPackageRepository) {
        events += "addCustom:${repo.name}"
      }
      override fun reinstateDefaultRepositoryNodes() {
        events += "addDefaults"
      }
    }

    presenter.rebuildTree(sink)

    assertEquals(listOf("clear", "addDefaults"), events)
  }
}
