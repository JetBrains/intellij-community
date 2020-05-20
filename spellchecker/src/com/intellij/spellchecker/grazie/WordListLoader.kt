// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.grazie.speller.lists.TraversableWordList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import gnu.trove.THashSet
import java.util.concurrent.atomic.AtomicBoolean

internal class WordListLoader(private val project: Project) {
  companion object {
    private val logger = Logger.getInstance(WordListLoader::class.java)
  }

  private val myIsLoadingList = AtomicBoolean(false)
  private val myListsToLoad: MutableList<Pair<Loader, (String, TraversableWordList) -> Unit>> = ContainerUtil.createLockFreeCopyOnWriteList()

  fun loadWordList(loader: Loader, consumer: (String, TraversableWordList) -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment) {
      consumer(loader.name, TraversableWordList.create(loader.readAll()))
    }
    else {
      loadWordListAsync(loader, consumer)
    }
  }

  private fun loadWordListAsync(loader: Loader, consumer: (String, TraversableWordList) -> Unit) {
    if (myIsLoadingList.compareAndSet(false, true)) {
      logger.debug("Loading ${loader.name}")
      doLoadWordListAsync(loader, consumer)
    }
    else {
      queueWordListLoad(loader, consumer)
    }
  }

  private fun doLoadWordListAsync(loader: Loader, consumer: (String, TraversableWordList) -> Unit) {
    if (project.isDefault) return

    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      logger.debug("Loading ${loader.name}")
      val app = ApplicationManager.getApplication()

      app.executeOnPooledThread {
        if (app.isDisposed) return@executeOnPooledThread

        logger.debug("${loader.name} loaded!")
        consumer(loader.name, TraversableWordList.create(loader.readAll()))

        while (myListsToLoad.isNotEmpty()) {
          if (app.isDisposed) return@executeOnPooledThread
          val (curLoader, curConsumer) = myListsToLoad.removeAt(0)

          logger.debug("${curLoader.name} loaded!")
          curConsumer(curLoader.name, TraversableWordList.create(curLoader.readAll()))
        }

        logger.debug("Loading finished, restarting daemon...")
        myIsLoadingList.set(false)

        UIUtil.invokeLaterIfNeeded {
          if (app.isDisposed) return@invokeLaterIfNeeded

          for (project in ProjectManager.getInstance().openProjects.filter { project.isInitialized && project.isOpen && !project.isDefault }) {
            DaemonCodeAnalyzer.getInstance(project)?.restart()
          }
        }
      }
    }
  }

  private fun Loader.readAll(): Set<String> {
    val set = THashSet<String>()
    load {
      set.add(it)
    }
    return set
  }

  private fun queueWordListLoad(loader: Loader, consumer: (String, TraversableWordList) -> Unit) {
    logger.debug("Queuing load for: ${loader.name}")
    myListsToLoad.add(loader to consumer)
  }
}