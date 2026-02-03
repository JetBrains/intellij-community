import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.EmptyCoroutineContext

val LocalString = compositionLocalOf { "Default" }
val LocalInt = staticCompositionLocalOf { 0 }

@Composable
fun CompositionLocalConsumer() {
    val stringValue = LocalString.current
    val intValue = LocalInt.current

    //Breakpoint!
    println("3: $stringValue, $intValue")
}

@Composable
fun CompositionLocalProvider() {
    CompositionLocalProvider(
        LocalString provides "String",
        LocalInt provides 42
    ) {
        //Breakpoint!
        println("2")
        CompositionLocalConsumer()
    }
}

fun composeTest(test: @Composable () -> Unit) {
    val applier = object : Applier<Unit> {
        override val current: Unit = Unit
        override fun clear() = Unit
        override fun down(node: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun up() = Unit
    }

    Composition(applier, Recomposer(EmptyCoroutineContext)).setContent {
        test()
    }
}

fun main() {
    //Breakpoint!
    println("1")

    composeTest {
        CompositionLocalProvider()
    }
}
