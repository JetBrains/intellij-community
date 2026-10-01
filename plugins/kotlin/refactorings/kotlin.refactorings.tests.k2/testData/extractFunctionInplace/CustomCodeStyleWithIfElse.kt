// CHANGED_NAME: extracted

class Action(val hasDelegate: Boolean)
class ComponentProvider
class Presentation
class Component

class X {
  private var lastActionClass = ""

  fun getOrCreateActionComponent(
    componentProvider: ComponentProvider?,
    action: Action,
    presentation: Presentation,
  ): Component {
    <selection>if (componentProvider != null) {
      return getCustomComponent(action, presentation, componentProvider)
    }
    else {
      if (action.hasDelegate) {
        println("Component provider is ignored due to wrapping: " +
                operationName(action, null, "toolbar"))
      }
      lastActionClass = action.javaClass.name
      return createToolbarButton(action, presentation, "toolbar")
    }</selection>
  }

  private fun getCustomComponent(
    action: Action,
    presentation: Presentation,
    componentProvider: ComponentProvider,
  ): Component = Component()

  private fun operationName(action: Action, value: Any?, place: String): String = ""

  private fun createToolbarButton(
    action: Action,
    presentation: Presentation,
    place: String,
  ): Component = Component()
}