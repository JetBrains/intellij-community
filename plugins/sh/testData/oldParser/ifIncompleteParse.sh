#error markers must be present, but the incomplete if should be parsed without remaining elements
if; then; fi
if a; then; fi
if a; then a; else; fi
if a; then; else; fi
if a; then; elif; then; else; fi
if a; then; elif then; else; fi
