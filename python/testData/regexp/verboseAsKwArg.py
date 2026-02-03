import re

linkfinder = re.compile(r"""\[                 # ein link beginnt mit eckiger klammer, escaped da [ sonst re-zeichen ist
(                  # nur der INHALT der [inhalt] Links ist gefragt
[^\[]+          # akzeptiere alles ausser oeffnender Klammer [ damit die Greediness umgangen wird.
)
\]
""", flags=re.VERBOSE)