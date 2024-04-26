import kotlin.reflect.KClass
internal fun KClass<*>.serializerNotRegistered(): Nothing {
    throw IllegalStateException(
        <selection>"Serializer for class '${simpleName}' is not found.\n" +
                "Mark the class as @Serializable or provide the serializer explicitly."</selection>
    )
}

// IGNORE_K1