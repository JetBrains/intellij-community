// The shell of a detail panel: a side panel on desktop and a bottom sheet below md. The card of an explainer node and
// the detail panel of a site entity render inside it.

import { XIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '../components/ui/button'
import { ScrollArea } from '../components/ui/scroll-area'

/** Mobile-only dismiss for the bottom-sheet presentation; desktop closes via pane click. */
function PanelClose({ onClose }: { onClose?: () => void }) {
  if (!onClose) {
    return null
  }
  return (
    <Button
      variant="outline"
      size="icon"
      className="md:hidden sticky top-0 float-right z-10 rounded-full"
      aria-label="Close details"
      onClick={onClose}
    >
      <XIcon />
    </Button>
  )
}

/* The panel shell: desktop side panel, bottom sheet below md (the e2e asserts position: fixed
   there). `detail-panel` stays as the marker for e2e and the `.detail-panel .markdown-body`
   sizing in canvas.css; heading/section rhythm is applied to descendants in one place here.
   Deliberately NOT a ui/sheet: the surface is non-modal (the canvas stays interactive beside
   it), open/close is route state (?sel=), and the mobile bottom-sheet presentation is a pure
   CSS fork — a Sheet's portal/overlay/focus-trap is exactly the behavior this must not have. */
export function DetailPanelShell({ onClose, children }: { onClose?: () => void, children: ReactNode }) {
  return (
    <aside className="detail-panel h-full w-[380px] border-l border-border bg-muted max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:z-60 max-md:h-auto max-md:max-h-[60dvh] max-md:w-auto max-md:rounded-t-xl max-md:border-t max-md:border-l-0 max-md:border-border max-md:shadow-lg [&_header_h2]:my-1.5 [&_header_h2]:text-lg [&_header_p]:text-sm [&_header_p]:text-muted-foreground [&_section]:mt-5.5 [&_h3]:mt-0 [&_h3]:mb-2 [&_h3]:text-xs [&_h3]:font-bold [&_h3]:tracking-[0.07em] [&_h3]:uppercase [&_h3]:text-muted-foreground">
      {/* the viewport carries the mobile height cap: the aside is h-auto there, so a bare
          h-full ScrollArea would have no bounded box to scroll inside */}
      <ScrollArea className="h-full max-md:[&_[data-slot=scroll-area-viewport]]:max-h-[60dvh]">
        <div className="p-5 pb-15 max-md:pb-[calc(40px+env(safe-area-inset-bottom))]">
          <PanelClose onClose={onClose} />
          {children}
        </div>
      </ScrollArea>
    </aside>
  )
}
