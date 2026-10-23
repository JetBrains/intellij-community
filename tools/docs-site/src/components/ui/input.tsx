import * as React from "react"
import { Input as InputPrimitive } from "@base-ui/react/input"
import { cn } from "cn"

// deviation from upstream: no text size class (text-xs, text-xs/relaxed, text-sm). A primitive takes the text size
// of its container: the IDE UI font size in the IDE, the prose size on a reading page.

// deviation from upstream: no bg-input/20 and no dark:bg-input/30. A field is the surface that it lies on, and the
// border alone marks its edge.

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-7 w-full min-w-0 rounded-md border border-input px-2 py-0.5 transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/20 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Input }
