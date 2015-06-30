lombok-intellij-plugin [![Build Status](https://travis-ci.org/mplushnikov/lombok-intellij-plugin.svg?branch=master)](https://travis-ci.org/mplushnikov/lombok-intellij-plugin) [![Donate](https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=3F9HXD7A2SMCN) 
======================

[![Join the chat at https://gitter.im/mplushnikov/lombok-intellij-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/mplushnikov/lombok-intellij-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Plugin for [IntelliJ IDEA](http://plugins.jetbrains.com/plugin/6317?pr=idea plugin) to support [Lombok](http://code.google.com/p/projectlombok/) annotations. ##

Provides support for lombok annotations to write great Java code with IntelliJ IDEA.

**Last version (0.9.5) released on 01.07.2015**

Twelfth version of plugin released. Bugfixes and initial support for some cool lombok and delombok refactoring actions with Intellij!

Install it automatically from IntelliJ Idea plugin repository.

Tested and supports IntelliJ versions: 12.1.7, 13.1.6, and 14.1.4  

Support for IntelliJ 11.1.5 by plugin version 0.9.1

Support for IntelliJ 10.5.4 by plugin version 0.8.7

With this plugin your IntelliJ can recognize all of generated getters, setters and some other things from lombok project, so that you get code completion and are able to work without errors stating the methods don't exists.

Features / Supports
--------
- [@Getter and @Setter](http://projectlombok.org/features/GetterSetter.html)
- [@ToString](http://projectlombok.org/features/ToString.html)
- [@EqualsAndHashCode](http://projectlombok.org/features/EqualsAndHashCode.html)
- [@AllArgsConstructor, @RequiredArgsConstructor and @NoArgsConstructor](http://projectlombok.org/features/Constructor.html)
- [@Log, @Log4j, @Log4j2, @Slf4j, @XSlf4j, @CommonsLog](http://projectlombok.org/features/Log.html)
- [@Data](http://projectlombok.org/features/Data.html)
- [@Builder](http://projectlombok.org/features/experimental/Builder.html)
- [@Delegate](http://projectlombok.org/features/Delegate.html)
- [@Value](http://projectlombok.org/features/Value.html)
- [@Accessors](http://projectlombok.org/features/experimental/Accessors.html)
- [@Wither](http://projectlombok.org/features/experimental/Wither.html)
- [@SneakyThrows](http://projectlombok.org/features/SneakyThrows.html)
- [@val](http://projectlombok.org/features/val.html) with IntelliJ 14.1
- lombok config files syntax highlighting
- code inspections
- refactoring actions (lombok and delombok)

Installation
------------
- Using IDE built-in plugin system on Windows:
  - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>Search for "lombok"</kbd> > <kbd>Install Plugin</kbd>
- Using IDE built-in plugin system on MacOs:
  - <kbd>Preferences</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Browse repositories...</kbd> > <kbd>Search for "lombok"</kbd> > <kbd>Install Plugin</kbd>
- Manually:
  - Download the [latest release](https://github.com/mplushnikov/lombok-intellij-plugin/releases/latest) and install it manually using <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Install plugin from disk...</kbd>
  
Restart IDE.

Developed By
------------
[**@mplushnikov** Michail Plushnikov] (https://github.com/mplushnikov)

**Contributors**
- [**@twillouer** William Delanoue](https://github.com/twillouer)
- [**@uvpoblotzki** Ulrich von Poblotzki](https://github.com/uvpoblotzki)
- [**@toadzky** David Harris](https://github.com/toadzky)
- [**@mlueders** Mike Lueders](https://github.com/mlueders)
- [**@mg6maciej** Maciej Górski](https://github.com/mg6maciej)
- [**@siosio** siosio](https://github.com/siosio)


License
-------
Copyright (c) 2011-2015 Michail Plushnikov. See the [LICENSE](./LICENSE) file for license rights and limitations (BSD).
