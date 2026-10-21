// The one rule of a page keyboard handler: a control that owns its keys keeps them.

/** Text fields, selects, and toggle groups own their keys: arrows move the highlight or the roving focus, and Escape closes the popup. */
export function ownsKeys(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null
  return element !== null && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable
    || element.closest('[data-slot="select-trigger"], [data-slot="select-content"], [data-slot="toggle-group"]') !== null)
}
