// Theme state: light | dark | system, persisted in localStorage. The resolved mode is applied
// as the `dark` class on <html> (shadcn convention) so CSS tokens and React Flow's colorMode
// stay in sync. index.html carries an inline copy of this logic so the first paint has the
// right class before this module loads — keep the storage key and resolution in sync with it.
// The site gives the storage key (`themeStorageKey` of the site config) to initTheme before it renders.

import { useEffect, useState } from 'react'

export type ThemeMode = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

let storageKey: string | null = null
const media = window.matchMedia('(prefers-color-scheme: dark)')

function storedMode(): ThemeMode {
  const value = storageKey === null ? null : localStorage.getItem(storageKey)
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  return mode === 'system' ? (media.matches ? 'dark' : 'light') : mode
}

function apply(mode: ThemeMode): void {
  document.documentElement.classList.toggle('dark', resolveTheme(mode) === 'dark')
}

/** Sets the localStorage key of the theme mode and applies the stored mode. A site calls it once, before it renders. */
export function initTheme(key: string): void {
  storageKey = key
  apply(storedMode())
}

/**
 * The currently applied theme, tracked via the `dark` class — usable from components
 * (e.g. React Flow's colorMode) that don't own the toggle.
 */
export function useResolvedTheme(): ResolvedTheme {
  const current = () => (document.documentElement.classList.contains('dark') ? 'dark' as const : 'light' as const)
  const [resolved, setResolved] = useState<ResolvedTheme>(current)
  useEffect(() => {
    const observer = new MutationObserver(() => setResolved(current()))
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])
  return resolved
}

export function useTheme(): { mode: ThemeMode, resolved: ResolvedTheme, set: (mode: ThemeMode) => void } {
  const [mode, setMode] = useState<ThemeMode>(storedMode)
  const [resolved, setResolved] = useState<ResolvedTheme>(() => resolveTheme(mode))

  useEffect(() => {
    apply(mode)
    setResolved(resolveTheme(mode))
    if (mode !== 'system') {
      return
    }
    const onChange = () => {
      apply('system')
      setResolved(resolveTheme('system'))
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [mode])

  const set = (next: ThemeMode) => {
    if (storageKey !== null) {
      if (next === 'system') {
        localStorage.removeItem(storageKey)
      }
      else {
        localStorage.setItem(storageKey, next)
      }
    }
    setMode(next)
  }

  return { mode, resolved, set }
}
