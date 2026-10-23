// Theme picker: Light / Dark / System dropdown driving the dark class via theme.ts.

import { MonitorIcon, MoonIcon, SunIcon } from 'lucide-react'
import { Button } from './ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from './ui/dropdown-menu'
import { type ThemeMode, useTheme } from '../theme'

const MODES: { mode: ThemeMode, label: string, icon: typeof SunIcon }[] = [
  { mode: 'light', label: 'Light', icon: SunIcon },
  { mode: 'dark', label: 'Dark', icon: MoonIcon },
  { mode: 'system', label: 'System', icon: MonitorIcon },
]

export function ThemeToggle() {
  const { mode, resolved, set } = useTheme()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={(
          <Button variant="ghost" size="icon-sm" className="theme-toggle text-muted-foreground hover:bg-hover aria-expanded:bg-hover" aria-label={`Theme: ${mode}`}>
            {resolved === 'dark' ? <MoonIcon /> : <SunIcon />}
          </Button>
        )}
      />
      <DropdownMenuContent align="end">
        <DropdownMenuRadioGroup value={mode} onValueChange={value => set(value as ThemeMode)}>
          {MODES.map(({ mode: value, label, icon: Icon }) => (
            <DropdownMenuRadioItem key={value} value={value} closeOnClick>
              <Icon />
              {label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
