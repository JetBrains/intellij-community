package com.intellij.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.picocontainer.MutablePicoContainer


class PerformanceTests : LightFixtureCompletionTestCase() {
    private lateinit var pathProvider: FilePathProvider
    
    private val runnable = "interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }"
    private val text = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r<caret>
    }
}
"""

    override fun setUp() {
        super.setUp()
        val container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        pathProvider = container.getComponentInstance(FilePathProvider::class.java.name) as FilePathProvider
    }

    override fun tearDown() {
        CompletionLoggerProvider.getInstance().dispose()
        //todo check if lookup is disposed and no more completion cancelled event is triggered
        val statsDir = pathProvider.getStatsDataDirectory()
        statsDir.deleteRecursively()
        super.tearDown()
    }

    fun `test do not block EDT on logging`() {
        myFixture.configureByText("Test.java", text)
        myFixture.addClass(runnable)

        
        val requestService = mock(RequestService::class.java)
        val anyString = Matchers.anyString()
        val anyMap = Matchers.anyMapOf(String::class.java, String::class.java)
        `when`(requestService.post(anyString, anyMap)).then { 
            Thread.sleep(5000)
            ResponseData(200)
        }

        val file = pathProvider.getUniqueFile()
        file.writeText("Some existing data to send")
        
        val sender = StatisticSender(requestService, pathProvider)
        
        ApplicationManager.getApplication().executeOnPooledThread { 
            sender.sendStatsData("")
        }
        Thread.sleep(300)
        
        val start = System.currentTimeMillis()
        myFixture.type('.')
        myFixture.completeBasic()
        myFixture.type("xxxx")
        val end = System.currentTimeMillis()

        val delta = end - start
        UsefulTestCase.assertTrue("Time on typing: $delta", delta < 2000)
    }

}
