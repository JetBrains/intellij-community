package com.stats.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import org.mockito.Mockito.*
import org.picocontainer.MutablePicoContainer

class CompletionTypingTest : LightFixtureCompletionTestCase() {
    lateinit var realStorage: EventLogger
    lateinit var mockStorage: EventLogger
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
        mockStorage = mock(EventLogger::class.java)
        
        val name = EventLogger::class.java.name
        realStorage = container.getComponentInstance(name) as EventLogger
        
        container.unregisterComponent(name)
        container.registerComponentInstance(name, mockStorage)
    }

    override fun tearDown() {
        val name = EventLogger::class.java.name
        container.unregisterComponent(name)
        container.registerComponentInstance(name, realStorage)
        super.tearDown()
    }
    
    fun `test item selected on just typing`() {
        myFixture.addClass("interface Runnable { void run();  void notify(); void wait(); void notifyAll(); }")
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
        myFixture.type("run(")
        verify(mockStorage, times(1)).itemSelected()
    }
    
    
}