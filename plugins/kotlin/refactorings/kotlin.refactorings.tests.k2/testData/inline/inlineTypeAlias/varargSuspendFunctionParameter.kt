typealias <caret>Ext = suspend String.() -> Int

fun takeExtensions(vararg es: Ext): List<Ext> = es.toList()
