PerformanceTesting plugin provides a way to execute commands inside IDE.
Command is an abstraction that performs an action using internal IntelliJ API. It's based on macroses but the main difference is that
there is a predefined set of commands that don't finish until the action is finished.

For example, when you invoke completion via macros - macros will be finished as soon as completion action is invoked.
If you use command `doComplete` the command will be finished when all the completion contributors have provided their results and the final
list is sorted.

To implement your own commands you need to create a plugin that extends performanceTestingPlugin.

Basic setup should look something like this:

Create a `resources/META-INF/plugin.xml`

```
<idea-plugin>
  <name>Your plugin name</name>
  <id>com.intellij.performancePlugin.myPlugin</id>

  <description>My integration tests</description>
  <depends>com.jetbrains.performancePlugin</depends>
  <depends>com.intellij.modules.lang</depends>

  <extensions defaultExtensionNs="com.jetbrains">
    <performancePlugin.commandProvider implementation="com.intellij.myPlugin.performanceTesting.MyPluginCommandProvider"/>
  </extensions>
</idea-plugin>
```

Then create a command provider

```
package com.intellij.myPlugin.performanceTesting

class MyPluginCommandProvider : CommandProvider {
  override fun getCommands() = mapOf(
      Pair(MyCommand.PREFIX, CreateCommand(::MyCommand)),
    )
}
```

And implementation of your own command

```
package com.intellij.myPlugin.performanceTesting.command

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter

internal class MyCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "myCommandName"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    TODO("YOUR CODE HERE")
  }
}
```

Test implementation (that will use Starter and tell the plugin to invoke your command) will like this

```
fun <T : CommandChain> T.runMyCommand(): T {
  addCommand(CMD_PREFIX + "myCommandName")
  return this
}

class ExampleOfMyCommandTest {

  @Test
  fun invokeMyCommand() {
    val context = Starter.newContext(testName = CurrentTestMethod.hyphenateWithClass(), testCase = TestCases.IU.JitPackAndroidExample)
      .skipIndicesInitialization() // skip indicies if indexing isn't necessary for test

    context.runIDE(
      commands = CommandChain()
        .runMyCommand()
        .exitApp()
    )
  }
}
```