// WITH_STDLIB
fun x(items : Sequence<String>) {}
fun y() {
    x(<selection>foo@ (sequenceOf(""))</selection>)
}
/*
items
of
sequence
sequenceOf
stringSequence
*/