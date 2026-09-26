import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun StatefulComponent(content: @Composable (MutableState<Int>) -> Unit) {
    val state = remember { mutableStateOf(0) }
    //Breakpoint!
    println("3: ${state.value}")
    content(state)
}

@Composable
fun Counter(state: MutableState<Int>) {
    //Breakpoint!
    println("4: ${state.value}")
    
    state.value += 1
    
    //Breakpoint!
    println("5: ${state.value}")
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
        StatefulComponent { state ->
            Counter(state)
            
            //Breakpoint!
            println("2: ${state.value}")
        }
    }
}
