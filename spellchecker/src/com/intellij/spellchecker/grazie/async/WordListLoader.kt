// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.async

import ai.grazie.spell.lists.WordList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.spellchecker.grazie.dictionary.SimpleWordList
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<WordListLoader>()

internal class WordListLoader(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val isLoadingList = AtomicBoolean(false)
  private val listsToLoad = ContainerUtil.createLockFreeCopyOnWriteList<Pair<Loader, (String, WordList) -> Unit>>()

  fun loadWordList(loader: Loader, consumer: (String, WordList) -> Unit) {
    if (AsyncUtils.isNonAsyncMode()) {
      consumer(loader.name, SimpleWordList(readAll(loader)))
    }
    else if (isLoadingList.compareAndSet(false, true)) {
      LOG.debug("Loading $loader")
      doLoadWordListAsync(loader, consumer)
    }
    else {
      queueWordListLoad(loader, consumer)
    }
  }

  private fun doLoadWordListAsync(loader: Loader, consumer: (String, WordList) -> Unit) {
    if (project.isDefault) {
      return
    }

    StartupManager.getInstance(project).runAfterOpened {
      LOG.debug { "Loading ${loader}" }

      coroutineScope.launch {
        LOG.debug("${loader} loaded!")
        consumer(loader.name, SimpleWordList(readAll(loader)))

        while (listsToLoad.isNotEmpty()) {
          val (curLoader, currentConsumer) = listsToLoad.removeAt(0)
          LOG.debug("${curLoader.name} loaded!")
          currentConsumer(curLoader.name, SimpleWordList(readAll(curLoader)))
        }

        LOG.debug("Loading finished, restarting daemon...")
        isLoadingList.set(false)

        withContext(Dispatchers.EDT) {
          AsyncUtils.restartInspection(ApplicationManager.getApplication())
        }
      }
    }
  }

  private fun readAll(loader: Loader): Set<String> {
    val set = CollectionFactory.createSmallMemoryFootprintSet<String>()
    loader.load {
      set.add(it)
    }
    return set
  }

  private fun queueWordListLoad(loader: Loader, consumer: (String, WordList) -> Unit) {
    LOG.debug("Queuing load for: ${loader.name}")
    listsToLoad.add(loader to consumer)
  }
}