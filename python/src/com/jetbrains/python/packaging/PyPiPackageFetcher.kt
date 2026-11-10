// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.withBasicAuthorization
import kotlinx.coroutines.currentCoroutineContext
import java.io.IOException
import java.io.Reader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import kotlin.coroutines.CoroutineContext
import com.jetbrains.python.Result

internal object PyPiPackageFetcher {
  private const val PYPI_BASE_URL = "https://pypi.org"
  private const val PYPI_SIMPLE_REPOSITORY_API = "/simple/"
  private const val PYPI_LIST_URL = PYPI_BASE_URL + PYPI_SIMPLE_REPOSITORY_API

  suspend fun fetchPipPackages(url: String = PYPI_LIST_URL): Result<List<String>, FetchError> {
    val coroutineContext = currentCoroutineContext()
    val service = ApplicationManager.getApplication().service<PyPackageRepositories>()
    val repository = service.repositories.firstOrNull { it.repositoryUrl == url }

    return try {
      HttpRequests
        .request(url)
        .productNameAsUserAgent()
        .also { builder ->
          repository?.let { repository -> builder.withBasicAuthorization(repository) }
        }
        .connect { request ->
          request.reader.parsePackageLinks(coroutineContext)
        }
        .let {
          Result.Success(it)
        }
    }
    catch (e: IOException) {
      Result.Failure(FetchError(e.localizedMessage ?: "IOException"))
    }
    catch (e: Throwable) {
      throw e
    }
  }

  private fun Reader.parsePackageLinks(coroutineContext: CoroutineContext): List<String> {
    val packages = mutableListOf<String>()

    CancelableParserDelegator(coroutineContext)
      .parse(
        this,
        object : HTMLEditorKit.ParserCallback() {
          private var currentTag: HTML.Tag? = null

          override fun handleStartTag(tag: HTML.Tag?, attributes: MutableAttributeSet?, pos: Int) {
            currentTag = tag
          }

          override fun handleText(data: CharArray?, pos: Int) {
            if (data == null || currentTag == null || currentTag.toString() != "a") {
              return
            }

            var packageName = String(data)

            if (packageName.endsWith("/")) {
              packageName = packageName.substring(0, packageName.indexOf("/"))
            }

            packages += packageName
          }

          override fun handleEndTag(tag: HTML.Tag?, pos: Int) {
            currentTag = null
          }
        },
        true
      )

    return packages
  }

  data class FetchError(val message: @NlsSafe String)
}