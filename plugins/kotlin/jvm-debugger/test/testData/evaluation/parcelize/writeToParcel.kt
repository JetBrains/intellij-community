// FILE: main.kt

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DataParcelable(
    val foo: Int,
    val bar: String?,
) : Parcelable

fun main() {
    val value = DataParcelable(foo = 3, bar = "test")
    //Breakpoint!
    println(value.foo)
}

// FILE: Parcelable.kt
// Fake the relevant Parcelable classes, so we don't need to depend on the
// entire Android SDK in this test.

package android.os

class Parcel

interface Parcelable {
    fun describeContents(): Int
    fun writeToParcel(dest: Parcel, flags: Int)

    interface Creator<T> {
        fun createFromParcel(source: Parcel?): T
        fun newArray(size: Int): Array<T>?
    }
}

// EXPRESSION: value.bar
// RESULT: "test": Ljava/lang/String;

// EXPRESSION: DataParcelable::writeToParcel
// RESULT: instance of Generated_for_debugger_class$generated_for_debugger_fun$1(id=ID): LGenerated_for_debugger_class$generated_for_debugger_fun$1;

// EXPRESSION: @Parcelize data class x(val y: Int, val z: String?) : Parcelable
// RESULT: Class 'x' is not abstract and does not implement abstract member 'writeToParcel'.