import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers

@Composable
fun LaunchedEffectComponent() {
    val counter = remember { mutableStateOf(0) }

    LaunchedEffect(counter.value) {
        //Breakpoint!
        println("${counter.value}")
    }

    counter.value += 1
}

fun main() {
    composeTest {
        LaunchedEffectComponent()
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

    Composition(applier, Recomposer(Dispatchers.Unconfined)).setContent {
        test()
    }
}

// EXPRESSION: counter.value
// RESULT: 1: I
