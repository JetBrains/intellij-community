package com.dependency

class Bar {
    companion object
}

fun Bar.bar(): Bar {
    return this
}