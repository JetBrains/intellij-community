import * as React from "react"
import { cn } from "cn"

// deviation from upstream: no text size class (text-xs, text-xs/relaxed, text-sm). A primitive takes the text size
// of its container: the IDE UI font size in the IDE, the prose size on a reading page.

// deviation from upstream: no bg-input/20 and no dark:bg-input/30. A field is the surface that it lies on, and the
// border alone marks its edge.

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex field-sizing-content min-h-16 w-full resize-none rounded-md border border-input px-2 py-2 transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/20 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
