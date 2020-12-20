package com.jetbrains.packagesearch.intellij.plugin.api

abstract class SearchClientTestsBase {

    protected fun createSearchClient(url: String = ServerURLs.base, timeout: Int = 10) =
            SearchClient(
                    baseUrl = url,
                    timeoutInSeconds = timeout,
                    headers = listOf(
                            Pair("JB-IDE-Version", "UNIT TEST"),
                            Pair("JB-Plugin-Version", "UNIT TEST")
                    )
            )
}
