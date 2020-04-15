lombok-intellij-plugin [![Donate](https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=3F9HXD7A2SMCN)
======================
[![Build Status][badge-travis-img]][badge-travis] [![Code Coverage](https://img.shields.io/codecov/c/github/mplushnikov/lombok-intellij-plugin/master.svg)](https://codecov.io/github/mplushnikov/lombok-intellij-plugin?branch=master)

[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/6317-lombok-plugin.svg)](https://plugins.jetbrains.com/plugin/6317-lombok-plugin)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/6317-lombok-plugin.svg)](https://plugins.jetbrains.com/plugin/6317-lombok-plugin)
[![Downloads last month](http://phpstorm.espend.de/badge/6317/last-month)](https://plugins.jetbrains.com/plugin/6317-lombok-plugin)

[![Gitter][badge-gitter-img]][badge-gitter] [![Donate][badge-paypal-img]][badge-paypal]


## Plugin for [IntelliJ IDEA](http://plugins.jetbrains.com/plugin/6317-lombok-plugin) to support [Lombok](https://projectlombok.org) annotations.

Provides support for lombok annotations to write great Java code with IntelliJ IDEA.

**Last version (0.29) released on 01.03.2020**

---

:collision: **The latest IntelliJ IDEA version (2020.1) contains a BUG :beetle: for several plugins (including plugin for Lombok) were shown as incompatible with the updated version of the IDE.** :collision:

You can find the issue with a detailed explanation here: https://youtrack.jetbrains.com/issue/IDEA-237113.

It has already been resolved and the fix will be in the nearest IDE patch (presumably 2020.1.1).

:hammer_and_wrench: **FIX:** As of now, the workaround is to re-install the plugin in the IDE settings.

---

34th version of plugin released.

Install it automatically from IntelliJ Idea plugin repository.

Tested and supports IntelliJ versions: 2016.2, 2016.3, 2017.X, 2018.X, 2019.1, 2019.2 and 2019.3

Last support for IntelliJ 15.0.6 and 2016.1 by plugin version 0.19!

Last support for IntelliJ 14.1.7 by plugin version 0.14!

Last support for IntelliJ 11.1.5, 12.1.7, 13.1.6 by plugin version 0.11

Last support for IntelliJ 10.5.4 by plugin version 0.8.7

With this plugin your IntelliJ can recognize all of generated getters, setters and some other things from lombok project, so that you get code completion and are able to work without errors stating the methods don't exists.


Features / Supports
--------
- [@Getter and @Setter](http://projectlombok.org/features/GetterSetter.html)
- [@ToString](http://projectlombok.org/features/ToString.html)
- [@EqualsAndHashCode](http://projectlombok.org/features/EqualsAndHashCode.html)
- [@AllArgsConstructor, @RequiredArgsConstructor and @NoArgsConstructor](http://projectlombok.org/features/Constructor.html)
- [@Log, @Log4j, @Log4j2, @Slf4j, @XSlf4j, @CommonsLog, @JBossLog, @Flogger, @CustomLog](http://projectlombok.org/features/Log.html)
- [@Data](https://projectlombok.org/features/Data.html)
- [@Builder](https://projectlombok.org/features/Builder.html)
- [@Singular](https://projectlombok.org/features/Builder.html#singular)
- [@Delegate](https://projectlombok.org/features/Delegate.html)
- [@Value](https://projectlombok.org/features/Value.html)
- [@Accessors](https://projectlombok.org/features/experimental/Accessors.html)
- [@With](https://projectlombok.org/features/With.html)
- [@SneakyThrows](https://projectlombok.org/features/SneakyThrows.html)
- [@val](https://projectlombok.org/features/val.html) available from IntelliJ 14.1 (improved in 2016.2)
- [@UtilityClass](https://projectlombok.org/features/experimental/UtilityClass.html) available from IntelliJ 2016.2
- [lombok config files syntax highlighting](https://projectlombok.org/features/configuration.html)
- code inspections
- refactoring actions (lombok and delombok)
- project configuration inspection (missing/out-of-date Lombok dependency, annotation processing disabled)

Many features of the plugin (including warnings) could be disabled through per-project settings.

Installation
------------
### Plugin Installation
- Using IDE built-in plugin system on Windows:
  - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>Search for "lombok"</kbd> > <kbd>Install Plugin</kbd>
- Using IDE built-in plugin system on MacOs:
  - <kbd>Preferences</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>Search for "lombok"</kbd> > <kbd>Install Plugin</kbd>
- Manually:
  - Download the [latest release](https://github.com/mplushnikov/lombok-intellij-plugin/releases/latest) and install it manually using <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>

Restart IDE.

### Required IntelliJ Configuration
In your project: Click <kbd>Preferences</kbd> -> <kbd>Build, Execution, Deployment</kbd> -> <kbd>Compiler, Annotation Processors</kbd>. Click <kbd>Enable Annotation Processing</kbd>

Afterwards you might need to do a complete rebuild of your project via <kbd>Build</kbd> -> <kbd>Rebuild Project</kbd>.

### Lombok project dependency
Make sure you have Lombok dependency added to your project. This plugin **does not** automatically add it for you.

**Please Note:** Using newest version of the Lombok dependency is recommended, but does not guarantee that all the features introduced will be available. See [Lombok changelog](https://projectlombok.org/changelog.html) for more details.

If you are using Gradle/Maven/Ivy, see example below:

##### Gradle
In your `build.gradle`:
```groovy
repositories {
	mavenCentral()
}

dependencies {
	compileOnly 'org.projectlombok:lombok:1.18.12'
	annotationProcessor 'org.projectlombok:lombok:1.18.12'

	testCompileOnly 'org.projectlombok:lombok:1.18.12'
	testAnnotationProcessor 'org.projectlombok:lombok:1.18.12'
}
```

##### Maven
In your `pom.xml`:
```xml
<dependencies>
	<dependency>
		<groupId>org.projectlombok</groupId>
		<artifactId>lombok</artifactId>
		<version>1.18.12</version>
		<scope>provided</scope>
	</dependency>
</dependencies>
```

##### Ivy
In your `ivy.xml`:
```xml
<dependency org="org.projectlombok" name="lombok" rev="1.18.12" conf="build" />
```

IntelliJ and Eclipse compiler
-----------------------------
If you're using Eclipse compiler with lombok, try this setup:
- install plugin
- make sure Lombok dependency is added to the project
- change compiler setting:
  - <kbd>...</kbd> > <kbd>Compiler</kbd> > <kbd>Java Compiler</kbd> > <kbd>Use Compiler: Eclipse</kbd>
  - <kbd>...</kbd> > <kbd>Compiler</kbd> > <kbd>Annotation Processors</kbd> > <kbd>Enable annotation processing: checked (default configuration)</kbd>
  - <kbd>...</kbd> > <kbd>Compiler</kbd> > <kbd>Additional build process VM options: -javaagent:lombok.jar</kbd>

Developed By
------------
[**@mplushnikov** Michail Plushnikov](https://github.com/mplushnikov)

**Contributors**
- [**@adamarmistead** adamarmistead](https://github.com/adamarmistead)
- [**@akozlova** Anna Kozlova](https://github.com/akozlova)
- [**@alanachtenberg** Alan Achtenberg](https://github.com/alanachtenberg)
- [**@AlexejK** Alexej Kubarev](https://github.com/AlexejK)
- [**@bulgakovalexander** Alexander Bulgakov](https://github.com/bulgakovalexander)
- [**@jeromewaibel** Jérôme Waibel](https://github.com/jeromewaibel)
- [**@Jessevanbekkum** Jesse van Bekkum](https://github.com/Jessevanbekkum)
- [**@juriad** Adam Juraszek](https://github.com/juriad)
- [**@krzyk** Krzysztof Krasoń](https://github.com/krzyk)
- [**@Lekanich** Aleksandr Zhelezniak](https://github.com/Lekanich)
- [**@mg6maciej** Maciej Górski](https://github.com/mg6maciej)
- [**@mlueders** Mike Lueders](https://github.com/mlueders)
- [**@RohanTalip** Rohan Talip](https://github.com/RohanTalip)
- [**@ruurd** Ruurd Pels](https://github.com/ruurd)
- [**@Sheigutn** Florian Böhm](https://github.com/Sheigutn)
- [**@siosio** siosio](https://github.com/siosio)
- [**@Siriah** Iris Hupkens](https://github.com/Siriah)
- [**@tlinkowski** Tomasz Linkowski](https://github.com/tlinkowski)
- [**@toadzky** David Harris](https://github.com/toadzky)
- [**@twillouer** William Delanoue](https://github.com/twillouer)
- [**@uvpoblotzki** Ulrich von Poblotzki](https://github.com/uvpoblotzki)
- [**@vanam** Martin Váňa](https://github.com/vanam)
- [**@yiftizur** Yiftach Tzur](https://github.com/yiftizur)
- [**@sluongng** Son Luong Ngoc](https://github.com/sluongng)
- [**@tagae** Sebastián González](https://github.com/tagae)
- [**@wyhasany** Michał Rowicki](https://github.com/wyhasany)
- [**@ocadaruma** Haruki Okada](https://github.com/ocadaruma)
- [**@mackatozis** Alexandros Efthymiadis](https://github.com/mackatozis)

Supporters
--------
[<img src="https://www.yourkit.com/images/yklogo.png" />](https://www.yourkit.com/)

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/index.jsp), innovative and intelligent tools for profiling Java and .NET applications.


License
-------
Copyright (c) 2011-2020 Michail Plushnikov. See the [LICENSE](./LICENSE) file for license rights and limitations (BSD).

[badge-gitter-img]:       https://badges.gitter.im/mplushnikov/lombok-intellij-plugin.svg
[badge-gitter]:           https://gitter.im/mplushnikov/lombok-intellij-plugin
[badge-travis-img]:       https://travis-ci.org/mplushnikov/lombok-intellij-plugin.svg
[badge-travis]:           https://travis-ci.org/mplushnikov/lombok-intellij-plugin
[badge-coveralls-img]:    https://coveralls.io/repos/github/mplushnikov/lombok-intellij-plugin/badge.svg?branch=master
[badge-coveralls]:        https://coveralls.io/github/mplushnikov/lombok-intellij-plugin?branch=master
[badge-paypal-img]:       https://img.shields.io/badge/donate-paypal-yellow.svg
[badge-paypal]:           https://www.paypal.me/mplushnikov
