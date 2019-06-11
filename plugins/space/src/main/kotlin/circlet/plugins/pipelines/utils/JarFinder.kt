package circlet.plugins.pipelines.utils

import com.intellij.ide.plugins.cl.*
import java.net.*
import kotlin.reflect.*

//todo check if it's legal
fun find(clazz: KClass<*>, name: String): URL {
    return (clazz.java.classLoader as PluginClassLoader).urls.firstOrNull {
        x -> x.file.contains("/$name")  }
        ?: error("can't find $name jar")

}
