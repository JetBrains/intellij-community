// COMPILER_ARGUMENTS: -Xplugin=$KOTLIN_BUNDLED$/lib/allopen-compiler-plugin.jar -P plugin:org.jetbrains.kotlin.allopen:annotation=test.MyOpener
package test

@MyOpener
class JavaClass {
    var field: Int = 0
}
