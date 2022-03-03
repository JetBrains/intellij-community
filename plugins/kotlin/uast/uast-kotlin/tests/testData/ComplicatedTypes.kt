import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

@PublishedApi
internal open class TypeBase<T>

public inline fun <reified T> typeTokenOf(): Type {
    val base = object : TypeBase<T>() {}
    val superType = base::class.java.genericSuperclass!!
    return (superType as ParameterizedType).actualTypeArguments.first()!!
}