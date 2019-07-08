package circlet.plugins.pipelines.utils

import com.intellij.ide.plugins.cl.*
import java.net.*
import kotlin.reflect.*

//todo check if it's legal
fun find(clazz: KClass<*>, name: String): URL {
    // todo fix this very very strange code
    val loader = clazz.java.classLoader
    when (loader) {
        is PluginClassLoader -> { // then idea starts with plugin
            return loader.urls.firstOrNull {
                x -> x.file.contains("/$name")  }
                ?: error("can't find $name jar")
        }

        is URLClassLoader -> { // then running tests
            return loader.urLs.firstOrNull {
                x -> x.file.contains("/$name")  }
                ?: error("can't find $name jar")
        }
        else -> {
            error("unknown classLoader $loader")
        }
    }
}
