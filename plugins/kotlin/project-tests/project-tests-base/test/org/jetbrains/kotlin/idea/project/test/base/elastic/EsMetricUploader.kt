// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.elastic


import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.performance.tests.utils.BuildData
import org.jetbrains.kotlin.idea.performance.tests.utils.logMessage
import org.jetbrains.kotlin.idea.project.test.base.ProjectData
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.metrics.MetricsData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*


object EsMetricUploader {
    fun upload(
        action: ProjectAction,
        project: ProjectData,
        frontend: KotlinPluginKind,
        iterations: List<MetricsData>,
        buildData: BuildData,
        elasticCredentials: ElasticCredentials
    ) {
        val json = MetricsToJson.toJsonString(action, project, buildData, frontend, iterations)
        logMessage { json }
        post(json, elasticCredentials)
    }

    private fun post(json: String, elasticCredentials: ElasticCredentials) {
        val client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI("${elasticCredentials.host}/${elasticCredentials.index}/_doc/"))
            .header("Authorization", basicAuth(elasticCredentials.username, elasticCredentials.password))
            .headers("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        logMessage { response.statusCode().toString() }
        logMessage { response.body() }
    }

    private fun basicAuth(username: String, password: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }

}
