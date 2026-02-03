import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

fun test(bean: Bean) {
    val prop0: KMutableProperty0<Boolean> = bean::isProp2
    val prop1: KMutableProperty1<Bean, Boolean> = Bean::isProp2
}
