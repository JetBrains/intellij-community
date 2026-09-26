import javaApi.Base

internal class C : Base() {
    fun f() {
        val other = Base()
        val value = other.getProperty() + getProperty()
        other.setProperty(1)
        setProperty(other.getProperty() + value)
        getBase(getProperty()).setProperty(0)
    }

    private fun getBase(i: Int): Base {
        return Base()
    }
}
