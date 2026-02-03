
with context_manager:
    assert False, f()
    print("Unreachable")

print("Reachable")