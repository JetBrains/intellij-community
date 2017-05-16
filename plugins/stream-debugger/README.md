# stream-debugger-plugin
In some ways, Stream API is better than traditional loop approach: it takes full advantage of modern multi-core architectures,
and lets you process data in a declarative way. What's also good is that this approach helps to avoid the state issues, and the
code written in it looks more elegant. But, there's a certain downside to it: the code sometimes is sure hard to read, understand,
and, of course, to debug.
      
This plugin is here to amend that and offer solutions to the issues you might run into. It extends the <em>Debugger</em>
tool window by adding the *Trace Current Stream Chain* button, which becomes active when debugger stops inside of a
chain of Stream API calls.

 ![](https://blog.jetbrains.com/idea/files/2017/05/Screen-Shot-2017-05-11-at-15.06.58.png)

After you click it, the current data stream is evaluated and you get a visualization of what exactly happens to each element
from the first call to last, with changes occurring gradually as it's passing thru all the steps:

![](https://blog.jetbrains.com/idea/files/2017/05/Screen-Shot-2017-05-11-at-15.06.18.png)

The *Split Mode button* in the left bottom corner lets you choose whether you want to see all operations at once or
separately:

![](https://blog.jetbrains.com/idea/files/2017/05/Screen-Shot-2017-05-11-at-15.04.39.png)

In the latter mode, you can switch between operations manually using the tabs on top.

Watch the following short animation to see these features in action:
![](https://blog.jetbrains.com/idea/files/2017/05/Screen-Shot-2017-05-11-at-15.07.27.gif)

The plugin is still under development, so expect a couple of glitches here and there, and, of course, we really appreciate your
feedback, including error reports, and we have set up an [issue tracker](https://youtrack.jetbrains.com/issues?q=Subsystem:%20%7BDebugger.%20Streams%7D) just for that.
