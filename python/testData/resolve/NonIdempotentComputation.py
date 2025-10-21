from typing import reveal_type

def solve(task: str) -> str:
    i = 0

    while True:
        # reveal_type(task)
        next_try = task + str(i)
        # reveal_type(next_try)
        # reveal_type(next_try.encode)
        res = next_try.encode("utf-8")
        #              <ref1>
        hex_hash = next_try[:2]
        # reveal_type(hex_hash)
        # reveal_type(hex_hash.startswith)
        if hex_hash.startswith("00000"):
        #           <ref2>
            i += 1
