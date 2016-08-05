package com.intellij.stats.completion.experiment

import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.ResponseData
import com.intellij.stats.completion.UrlProvider
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock


class StatusInfoProviderTest : LightIdeaTestCase() {

    fun `test experiment info is fetched`() {
        val requestSender = mock(RequestService::class.java)
        `when`(requestSender.get(Matchers.anyString()))
                .thenReturn(ResponseData(200, "{ performExperiment : true, experimentVersion: 10 }"))

        val urlProvider = mock(UrlProvider::class.java)
        `when`(urlProvider.experimentDataUrl).thenReturn("http://qq.da")

        val helper = StatusInfoProvider(requestSender, urlProvider)
        helper.unsetLastUpdate()
        
        val performExperiment = helper.isPerformExperiment()
        val version = helper.getExperimentVersion()
        
        UsefulTestCase.assertEquals(performExperiment, true)
        UsefulTestCase.assertEquals(version, 10)
    }

}