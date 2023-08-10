// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater.impl

import org.jetbrains.tools.model.updater.impl.JpsLibrary.LibraryType.Repository.Companion.ArtifactVerificationEntry
import java.net.URL
import java.security.MessageDigest

fun computeSha256Checksums(classesRoots: List<JpsUrl>, remoteRepository: JpsRemoteRepository): List<ArtifactVerificationEntry> {
    return classesRoots.map {
        require(it.path is JpsPath.MavenRepository)
        val sha256checksum = downloadFileAndComputeSha256("${remoteRepository.url}/${it.path.relativePath}")
        val fileUrl = JpsUrl.File(it.path)
        ArtifactVerificationEntry(fileUrl.url, sha256checksum)
    }
}

private fun downloadFileAndComputeSha256(url: String): String {
    println("Computing SHA256 checksum for '$url'")
    val digest = MessageDigest.getInstance("SHA-256")

    URL(url).openStream().use { inputStream ->
        val buf = ByteArray(65536)

        while (true) {
            val count = inputStream.read(buf)
            if (count <= 0) break
            digest.update(buf, 0, count)
        }
    }

    return byteArrayToHexString(digest.digest())
}

private fun byteArrayToHexString(bytes: ByteArray): String {
    val builder = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        builder.append(String.format("%02x", b))
    }
    return builder.toString()
}