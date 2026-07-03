// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useState } from "react"

interface FloatingTableOfContentsProps {
  markdown: string
}

interface TableOfContentsEntry {
  id: string
  text: string
  level: number
  element: HTMLElement
}

const headingSelector = "h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]"
const activeHeadingTopOffset = 80

export function FloatingTableOfContents({ markdown }: FloatingTableOfContentsProps) {
  const [entries, setEntries] = useState<TableOfContentsEntry[]>([])
  const [expanded, setExpanded] = useState(false)
  const [activeId, setActiveId] = useState<string | undefined>()

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

  if (entries.length < 2) return null

  if (!expanded) {
    return (
      <button
        type="button"
        className="markdownTocRail"
        title="Table of contents"
        aria-label="Show table of contents"
        aria-expanded="false"
        onClick={() => setExpanded(true)}
      >
        <span className="markdownTocRailIcon" aria-hidden="true" />
      </button>
    )
  }

  function scrollToEntry(entry: TableOfContentsEntry): void {
    const target = document.getElementById(entry.id)
    if (!target) return
    target.scrollIntoView({ block: "start", behavior: "smooth" })
    setActiveId(entry.id)
  }

  return (
    <nav
      className="markdownTocPanel"
      aria-label="Table of contents"
      onKeyDown={event => {
        if (event.key === "Escape") setExpanded(false)
      }}
    >
      <div className="markdownTocHeader">
        <span className="markdownTocTitle">Contents</span>
        <button
          type="button"
          className="markdownTocCollapseButton"
          title="Collapse"
          aria-label="Collapse table of contents"
          aria-expanded="true"
          onClick={() => setExpanded(false)}
        >
          <span className="markdownTocCollapseIcon" aria-hidden="true" />
        </button>
      </div>
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

function classNames(...names: Array<string | undefined>): string | undefined {
  const className = names.filter(Boolean).join(" ")
  return className || undefined
}
