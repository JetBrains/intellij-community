#### `intellij.tools.devLauncher` module

Provides class [IntellijDevLauncher](src/IntellijDevLauncher.kt) which is used to launch IntelliJ product using module-based loader from 
source code.

It's important to have as few dependencies as possible in this module, because all these dependencies will be loaded by the system 
classloader. If some of its dependencies are loaded by a different classloader in the product, their presence in the system classloader's 
classpath may cause errors.