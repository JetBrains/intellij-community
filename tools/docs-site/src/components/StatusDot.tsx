// The dot of a decision record status, in a record header and in the section tree.

import type { AdrStatus } from '../../scripts/docMeta.ts'
import { cn } from '../lib/utils'

/** The dot of a decision record status: a filled dot for a status in force, a ring for a replaced one. */
export function StatusDot({ status, className }: { status: AdrStatus, className?: string }) {
  return (
    <span
      className={cn(
        'status-dot inline-block size-[7px] flex-none rounded-full',
        status === 'accepted' && 'bg-success',
        status === 'proposed' && 'bg-sidebar-primary',
        status === 'partly-superseded' && 'ring-[1.5px] ring-warning ring-inset',
        status === 'superseded' && 'ring-[1.5px] ring-muted-foreground ring-inset',
        className,
      )}
      data-status={status}
      aria-hidden="true"
    />
  )
}
