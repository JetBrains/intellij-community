tests = "foo"
for t in tests:
    try:
        for t in []:
            print t
    except Exception:
        continue
