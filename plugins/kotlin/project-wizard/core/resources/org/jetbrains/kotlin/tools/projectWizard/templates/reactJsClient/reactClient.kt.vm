import react.create
import react.dom.client.createRoot
import web.dom.document

fun main() {
    val container = document.createElement("div")
    document.body.appendChild(container)

    val welcome = Welcome.create {
        name = "Kotlin/JS"
    }
    createRoot(container).render(welcome)
}