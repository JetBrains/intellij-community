package com.intellij.ide.starter

import com.intellij.ide.starter.sdk.go.GoSdkDownloaderFacade
import java.nio.file.Path

/**
 * Downloads Go SDK for the specified version.
 * This is a convenience function that delegates to [GoSdkDownloaderFacade].
 *
 * For more control over the download process, use [GoSdkDownloaderFacade.goSdk] directly.
 *
 * @param version Go SDK version (e.g., "1.21.0")
 * @return Path to the Go SDK home directory
 */
fun downloadGoSdk(version: String): Path = GoSdkDownloaderFacade.goSdk(version).home
