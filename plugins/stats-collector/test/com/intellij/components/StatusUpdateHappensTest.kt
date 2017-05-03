package com.intellij.components

import com.intellij.mocks.TestRequestService
import com.intellij.sorting.WebServiceMock
import com.intellij.stats.completion.FilePathProvider
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.SenderComponent
import com.intellij.stats.completion.StatisticSender
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.testFramework.LightPlatformTestCase
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions.assertThat


class StatusUpdateHappensTest: LightPlatformTestCase() {

    fun `test sender component updates status`() {
        val statsSender = StatisticSender(mock<RequestService>(), mock<FilePathProvider>())
        val statusHelper = mock<WebServiceStatus>()
        val senderComponent = SenderComponent(statsSender, statusHelper)

        senderComponent.initComponent()
        Thread.sleep(100)

        verify(statusHelper, times(1)).updateStatus()
        verify(statusHelper, never()).isPerformExperiment()

        senderComponent.disposeComponent()
    }

    fun `test model turning on performExperiment`() {
        val status = WebServiceStatus.getInstance()

        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = false)
        status.updateStatus()
        assertThat(status.isPerformExperiment()).isFalse()

        TestRequestService.mock = WebServiceMock.mockRequestService(performExperiment = true)
        status.updateStatus()
        assertThat(status.isPerformExperiment()).isTrue()
    }

}