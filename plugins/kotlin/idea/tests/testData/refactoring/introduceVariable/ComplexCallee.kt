// WITH_STDLIB

fun fn(index : Int, list: List<()->Unit>) {
    when  {
        index in list.indices -> <selection>list[index]</selection>()
    }
}