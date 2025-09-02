// PARAM_DESCRIPTOR: value-parameter item: Item defined in a
// PARAM_TYPES: Item

data class Item(val sellByDate: Int, val quality: Int)

fun a(item: Item): Item {
    return <selection>item.copy(quality = item.quality - 1)</selection>
}
