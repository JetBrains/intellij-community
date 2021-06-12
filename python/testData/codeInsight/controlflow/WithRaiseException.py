
with context_manager:
    if c:
        raise ValueError
    val = c

print(val)