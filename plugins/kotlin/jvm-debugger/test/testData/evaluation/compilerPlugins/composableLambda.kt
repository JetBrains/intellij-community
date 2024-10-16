import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.coroutines.EmptyCoroutineContext


@Composable
fun Foo(child: @Composable () -> Unit) {
    child()
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
    println("1")

    composeTest {
        Foo {
            println("2")

            Foo {
                //Breakpoint!
                println("3")
            }
        }
    }
}

fun getString() = "empty"

// EXPRESSION: getString()
// RESULT: "empty": Ljava/lang/String;

// EXPRESSION: Foo(::getString)
// RESULT: @Composable invocations can only happen from the context of a @Composable function