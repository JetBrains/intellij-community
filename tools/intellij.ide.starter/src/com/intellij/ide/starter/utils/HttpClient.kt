package com.intellij.ide.starter.utils

import com.intellij.ide.starter.utils.FileSystem.isFileUpToDate
import org.apache.commons.io.FileUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

// TODO: migrate on okhttp ?
object HttpClient {
  private val locks = ConcurrentHashMap<String, Semaphore>()

  fun <Y> sendRequest(request: HttpUriRequest, processor: (HttpResponse) -> Y): Y {
    HttpClientBuilder.create().build().use { client ->
      client.execute(request).use { response ->
        return processor(response)
      }
    }
  }

  fun download(url: String, outFile: File) = download(url, outFile.toPath())

  fun download(url: String, outPath: Path) {
    val lock = locks.getOrPut(outPath.toAbsolutePath().toString()) { Semaphore(1) }
    lock.acquire()

    try {
      logOutput("Downloading $url to $outPath")

      withRetry {
        sendRequest(HttpGet(url)) { response ->
          require(response.statusLine.statusCode == 200) { "Failed to download $url: $response" }

          outPath.parent.createDirectories()
          outPath.outputStream().buffered(10 * 1024 * 1024).use { stream ->
            response.entity?.writeTo(stream)
          }
        }
      }
    }
    finally {
      lock.release()
    }
  }

  fun downloadIfMissing(url: String, targetFile: Path) {
    val lock = locks[targetFile.toAbsolutePath().toString()]
    lock?.tryAcquire()

    try {
      if (url.contains("https://github.com")) {
        if (!targetFile.isFileUpToDate()) {
          targetFile.toFile().delete()
        }
      }

      if (targetFile.isRegularFile() && targetFile.fileSize() > 0) {
        logOutput("File $targetFile was already downloaded. Size ${FileUtils.byteCountToDisplaySize(targetFile.fileSize())}")
        return
      }
    }
    finally {
      lock?.release()
    }

    download(url, targetFile)
  }
}