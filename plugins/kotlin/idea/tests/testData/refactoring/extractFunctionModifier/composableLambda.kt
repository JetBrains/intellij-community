package com.example

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.VALUE_PARAMETER)
annotation class MyComposable

@MyComposable
fun myWidget(context: @MyComposable () -> Unit) {}

fun setContent() {
    myWidget {
        <selection>print(true)</selection>
    }
}