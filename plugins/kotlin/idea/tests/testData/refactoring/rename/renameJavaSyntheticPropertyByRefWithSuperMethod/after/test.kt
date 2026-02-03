import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

fun test(bean: Bean) {
    val prop0: KMutableProperty0<String> = bean::prop2
    val prop1: KMutableProperty1<Bean, String> = Bean::prop2
}
