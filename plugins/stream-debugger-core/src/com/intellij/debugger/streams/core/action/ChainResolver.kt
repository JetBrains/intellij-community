// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.action

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

enum class ChainStatus {
  LANGUAGE_NOT_SUPPORTED,
  COMPUTING,
  FOUND,
  NOT_FOUND
}
/**
 * Helps [TraceStreamAction] understand if there is a suitable chain under the debugger position or not.
 */
class ChainResolver {
  private val mySearchResult: AtomicReference<ChainsSearchResult> = AtomicReference<ChainsSearchResult>(ChainsSearchResult(0, -1, null))

  fun tryFindChain(elementAtDebugger: PsiElement): ChainStatus {
    var result = mySearchResult.get()
    if (result.isSuitableFor(elementAtDebugger)) {
      return result.chainsStatus
    }

    result = ChainsSearchResult.Companion.of(elementAtDebugger)
    checkChainsExistenceInBackground(elementAtDebugger, result)
    mySearchResult.set(result)
    return result.chainsStatus
  }

  internal fun getChains(elementAtDebugger: PsiElement): List<StreamChainWithLibrary> {
    val result = mySearchResult.get()
    if (!result.isSuitableFor(elementAtDebugger) || result.chainsStatus != ChainStatus.FOUND) {
      LOG.error("Cannot build chains: " + result.chainsStatus)
      return emptyList()
    }

    val elementLanguageId = elementAtDebugger.getLanguage().id
    val provider = LibrarySupportProvider.EP_NAME.findFirstSafe { it.getLanguageId() == elementLanguageId && it.getChainBuilder().isChainExists(elementAtDebugger) }
    if (provider != null) {
      val result = provider.chainBuilder.build(elementAtDebugger).map { StreamChainWithLibrary(it, provider) }.toList()
      return result
    }

    return emptyList()
  }

  internal class StreamChainWithLibrary(@JvmField val chain: StreamChain, @JvmField val provider: LibrarySupportProvider)

  private class ChainsSearchResult(val elementHash: Long, val offset: Long, containingFile: PsiFile?) {
    val fileModificationStamp: Long

    @Volatile
    var chainsStatus: ChainStatus = ChainStatus.COMPUTING

    init {
      fileModificationStamp = getModificationStamp(containingFile)
    }

    fun updateStatus(found: Boolean) {
      LOG.assertTrue(ChainStatus.COMPUTING == chainsStatus)
      chainsStatus = if (found) ChainStatus.FOUND else ChainStatus.NOT_FOUND
    }

    fun markUnsupportedLanguage() {
      LOG.assertTrue(ChainStatus.COMPUTING == chainsStatus)
      chainsStatus = ChainStatus.LANGUAGE_NOT_SUPPORTED
    }

    fun isSuitableFor(element: PsiElement): Boolean {
      return elementHash == element.hashCode().toLong() && offset == element.getTextOffset().toLong() && fileModificationStamp == getModificationStamp(
        element.getContainingFile())
    }

    companion object {
      private fun getModificationStamp(file: PsiFile?): Long {
        return if (file == null) -1 else file.getModificationStamp()
      }

      fun of(element: PsiElement): ChainsSearchResult {
        return ChainsSearchResult(element.hashCode().toLong(), element.getTextOffset().toLong(), element.getContainingFile())
      }
    }
  }

  companion object {

    private val LOG = Logger.getInstance(ChainResolver::class.java)

    @RequiresBackgroundThread
    private fun checkChainsExistenceInBackground(
      elementAtDebugger: PsiElement,
      searchResult: ChainsSearchResult
    ) {
      val extensions: List<LibrarySupportProvider> = forLanguage(elementAtDebugger.getLanguage())
      if (extensions.isEmpty()) {
        searchResult.markUnsupportedLanguage()
      }
      else {
        ReadAction.nonBlocking(Runnable {
          var found = false
          for (provider in extensions) {
            try {
              if (provider.getChainBuilder().isChainExists(elementAtDebugger)) {
                found = true
                break
              }
            }
            catch (e: ProcessCanceledException) {
              throw e
            }
            catch (ignored: PsiInvalidElementAccessException) {
            }
            catch (e: Throwable) {
              LOG.error(e)
            }
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Chains found:" + found)
          }
          searchResult.updateStatus(found)
        })
          .inSmartMode(elementAtDebugger.getProject())
          .executeSynchronously()
      }
    }

    private fun forLanguage(language: Language): List<LibrarySupportProvider> {
      return LibrarySupportProvider.EP_NAME.getByGroupingKey<String>(language.getID(), ChainResolver::class.java,
                                                                     Function { obj: LibrarySupportProvider? -> obj!!.getLanguageId() })
    }
  }
}
