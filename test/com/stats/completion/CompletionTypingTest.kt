package com.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer

class CompletionTypingTest : LightFixtureCompletionTestCase() {
    lateinit var mockLogger: CompletionLogger
    lateinit var realLoggerProvider: CompletionLoggerProvider
    lateinit var mockLoggerProvider: CompletionLoggerProvider
    lateinit var container: MutablePicoContainer
    
    val text = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r.<caret>
    }
}
"""
    
    override fun setUp() {
        super.setUp()
        container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
        mockLoggerProvider = mock(CompletionLoggerProvider::class.java)
        mockLogger = mock(CompletionLogger::class.java)
        `when`(mockLoggerProvider.newCompletionLogger()).thenReturn(mockLogger)
        
        val name = CompletionLoggerProvider::class.java.name
        realLoggerProvider = container.getComponentInstance(name) as CompletionLoggerProvider
        
        container.unregisterComponent(name)
        container.registerComponentInstance(name, mockLoggerProvider)
    }

    override fun tearDown() {
        val name = CompletionLoggerProvider::class.java.name
        container.unregisterComponent(name)
        container.registerComponentInstance(name, realLoggerProvider)
        super.tearDown()
    }
    
    fun `test item selected on just typing`() {
        myFixture.addClass("interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }")
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
        myFixture.type("run(")
        verify(mockLogger, times(1)).itemSelectedCompletionFinished()
    }
    
    
}