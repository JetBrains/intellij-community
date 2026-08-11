// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { addWebViewFocusLeaveListener, AllIcons } from "@jetbrains/intellij-webview"
import { useEffect, useMemo, useState, type ChangeEvent } from "react"
import type { MarkdownPreviewSettings } from "./markdownPreviewTypes"

interface FloatingMarkdownControlsProps {
  markdown: string
  settings: MarkdownPreviewSettings
  onSetFontSize: (fontSize: number) => void
}

interface TableOfContentsEntry {
  id: string
  text: string
  level: number
  element: HTMLElement
}

const headingSelector = "h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]"
const activeHeadingTopOffset = 80
type FloatingPanel = "toc" | "settings"

export function FloatingMarkdownControls({ markdown, settings, onSetFontSize }: FloatingMarkdownControlsProps) {
  const [entries, setEntries] = useState<TableOfContentsEntry[]>([])
  const [openPanel, setOpenPanel] = useState<FloatingPanel | undefined>()
  const [activeId, setActiveId] = useState<string | undefined>()
  const hasTableOfContents = entries.length >= 2
  const fontSizeOptions = useMemo(() => normalizedFontSizeOptions(settings), [settings])
  const currentFontSizeIndex = fontSizeIndex(settings.effectiveFontSize, fontSizeOptions)

  useEffect(() => {
    const nextEntries = collectTableOfContentsEntries()
    setEntries(nextEntries)
    setActiveId(nextEntries[0]?.id)
  }, [markdown])

  useEffect(() => {
    if (entries.length < 2) {
      setActiveId(undefined)
      return
    }

    let scheduledFrame: number | undefined
    const updateActiveHeading = (): void => {
      let active = entries[0].id
      for (const entry of entries) {
        if (entry.element.getBoundingClientRect().top <= activeHeadingTopOffset) {
          active = entry.id
        }
        else {
          break
        }
      }
      setActiveId(current => current === active ? current : active)
    }
    const scheduleUpdate = (): void => {
      if (scheduledFrame !== undefined) return
      scheduledFrame = window.requestAnimationFrame(() => {
        scheduledFrame = undefined
        updateActiveHeading()
      })
    }

    scheduleUpdate()
    window.addEventListener("scroll", scheduleUpdate, { passive: true })
    window.addEventListener("resize", scheduleUpdate)
    return () => {
      if (scheduledFrame !== undefined) window.cancelAnimationFrame(scheduledFrame)
      window.removeEventListener("scroll", scheduleUpdate)
      window.removeEventListener("resize", scheduleUpdate)
    }
  }, [entries])

  useEffect(() => {
    return addWebViewFocusLeaveListener(() => setOpenPanel(undefined))
  }, [])

  useEffect(() => {
    if (openPanel === undefined) return

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") setOpenPanel(undefined)
    }
    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [openPanel])

  useEffect(() => {
    if (!hasTableOfContents && openPanel === "toc") setOpenPanel(undefined)
  }, [hasTableOfContents, openPanel])

  function togglePanel(panel: FloatingPanel): void {
    setOpenPanel(current => current === panel ? undefined : panel)
  }

  function closePanel(): void {
    setOpenPanel(undefined)
  }

  function scrollToEntry(entry: TableOfContentsEntry): void {
    const target = document.getElementById(entry.id)
    if (!target) return
    target.scrollIntoView({ block: "start", behavior: "smooth" })
    setActiveId(entry.id)
  }

  function requestFontSize(fontSize: number): void {
    const normalizedFontSize = closestFontSize(fontSize, fontSizeOptions)
    if (normalizedFontSize === settings.effectiveFontSize) return
    onSetFontSize(normalizedFontSize)
  }

  function handleSliderChange(event: ChangeEvent<HTMLInputElement>): void {
    const requestedIndex = Number(event.currentTarget.value)
    const requestedFontSize = fontSizeOptions[requestedIndex]
    if (requestedFontSize !== undefined) requestFontSize(requestedFontSize)
  }

  return (
    <>
      <div className="markdownFloatingRail" aria-label="Markdown preview controls">
        {hasTableOfContents && (
          <button
            type="button"
            className={classNames("markdownFloatingRailButton", openPanel === "toc" ? "is-active" : undefined)}
            title="Table of contents"
            aria-label="Show table of contents"
            aria-expanded={openPanel === "toc"}
            onClick={() => togglePanel("toc")}
          >
            <span className="markdownTocRailIcon" aria-hidden="true" />
          </button>
        )}
        <button
          type="button"
          className={classNames("markdownFloatingRailButton", openPanel === "settings" ? "is-active" : undefined)}
          title="Font size settings"
          aria-label="Show font size settings"
          aria-expanded={openPanel === "settings"}
          onClick={() => togglePanel("settings")}
        >
          <img className="markdownFloatingRailIcon" src={AllIcons.src("general/settings.svg")} alt="" draggable={false} />
        </button>
      </div>
      {openPanel === "toc" && hasTableOfContents && (
        <nav
          className="markdownFloatingPanel markdownTocPanel"
          aria-label="Table of contents"
        >
          <FloatingPanelHeader title="Contents" closeLabel="Collapse table of contents" onClose={closePanel} />
          <ol className="markdownTocList">
            {entries.map(entry => (
              <li key={entry.id} className="markdownTocItem">
                <button
                  type="button"
                  className={classNames("markdownTocLink", entry.id === activeId ? "is-active" : undefined)}
                  style={{ paddingLeft: `${8 + Math.max(0, entry.level - 1) * 12}px` }}
                  aria-current={entry.id === activeId ? "location" : undefined}
                  title={entry.text}
                  onClick={() => scrollToEntry(entry)}
                >
                  <span className="markdownTocText">{entry.text}</span>
                </button>
              </li>
            ))}
          </ol>
        </nav>
      )}
      {openPanel === "settings" && (
        <section
          className="markdownFloatingPanel markdownFontSettingsPanel"
          aria-label="Options"
        >
          <FloatingPanelHeader title="Options" closeLabel="Collapse font size settings" onClose={closePanel} />
          <div className="markdownFontSettingsBody">
            <div className="markdownFontSetting">
              <div className="markdownFontSizeHeader">
                <label className="markdownFontSizeSliderLabel" htmlFor="markdown-font-size-slider">Font size</label>
                <div className="markdownFontSizeValue">{settings.effectiveFontSize} px</div>
              </div>
              <input
                id="markdown-font-size-slider"
                type="range"
                className="markdownFontSizeSlider"
                min={0}
                max={Math.max(0, fontSizeOptions.length - 1)}
                step={1}
                value={currentFontSizeIndex}
                disabled={fontSizeOptions.length < 2}
                aria-valuetext={`${settings.effectiveFontSize} px`}
                onChange={handleSliderChange}
              />
              <div className="markdownFontSizeButtons" aria-label="Font size controls">
                <FontSizeButton
                  icon="general/remove.svg"
                  label="Decrease font size"
                  disabled={currentFontSizeIndex <= 0}
                  onClick={() => requestFontSize(fontSizeOptions[currentFontSizeIndex - 1] ?? settings.effectiveFontSize)}
                />
                <FontSizeButton
                  icon="general/reset.svg"
                  label="Reset font size"
                  disabled={settings.effectiveFontSize === settings.defaultFontSize}
                  onClick={() => requestFontSize(settings.defaultFontSize)}
                />
                <FontSizeButton
                  icon="general/add.svg"
                  label="Increase font size"
                  disabled={currentFontSizeIndex >= fontSizeOptions.length - 1}
                  onClick={() => requestFontSize(fontSizeOptions[currentFontSizeIndex + 1] ?? settings.effectiveFontSize)}
                />
              </div>
            </div>
          </div>
        </section>
      )}
    </>
  )
}

interface FloatingPanelHeaderProps {
  title: string
  closeLabel: string
  onClose: () => void
}

function FloatingPanelHeader({ title, closeLabel, onClose }: FloatingPanelHeaderProps) {
  return (
    <div className="markdownFloatingPanelHeader">
      <span className="markdownFloatingPanelTitle">{title}</span>
      <button
        type="button"
        className="markdownFloatingPanelCloseButton"
        title="Collapse"
        aria-label={closeLabel}
        onClick={onClose}
      >
        <span className="markdownFloatingPanelCloseIcon" aria-hidden="true" />
      </button>
    </div>
  )
}

interface FontSizeButtonProps {
  icon: string
  label: string
  disabled: boolean
  onClick: () => void
}

function FontSizeButton({ icon, label, disabled, onClick }: FontSizeButtonProps) {
  return (
    <button
      type="button"
      className="markdownFontSizeButton"
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <img src={AllIcons.src(icon)} alt="" draggable={false} />
    </button>
  )
}

function collectTableOfContentsEntries(): TableOfContentsEntry[] {
  const contentElement = document.getElementById("content")
  if (!contentElement) return []

  return Array.from(contentElement.querySelectorAll<HTMLElement>(headingSelector))
    .map(heading => {
      const text = normalizeHeadingText(heading.textContent ?? "")
      if (!heading.id || !text || heading.classList.contains("sr-only") || heading.closest(".footnotes")) return undefined
      return {
        id: heading.id,
        text,
        level: headingLevel(heading),
        element: heading,
      }
    })
    .filter((entry): entry is TableOfContentsEntry => entry !== undefined)
}

function headingLevel(heading: HTMLElement): number {
  const level = Number(heading.tagName.substring(1))
  return Number.isFinite(level) ? Math.min(6, Math.max(1, level)) : 1
}

function normalizeHeadingText(text: string): string {
  return text.replace(/\s+/g, " ").trim()
}

function normalizedFontSizeOptions(settings: MarkdownPreviewSettings): number[] {
  return Array.from(new Set([...settings.fontSizeOptions, settings.effectiveFontSize, settings.defaultFontSize]
    .filter(value => Number.isFinite(value) && value > 0)))
    .sort((left, right) => left - right)
}

function fontSizeIndex(fontSize: number, options: number[]): number {
  return Math.max(0, options.indexOf(closestFontSize(fontSize, options)))
}

function closestFontSize(fontSize: number, options: number[]): number {
  return options.reduce((closest, candidate) => {
    return Math.abs(candidate - fontSize) < Math.abs(closest - fontSize) ? candidate : closest
  }, options[0] ?? fontSize)
}

function classNames(...names: Array<string | undefined>): string | undefined {
  const className = names.filter(Boolean).join(" ")
  return className || undefined
}
