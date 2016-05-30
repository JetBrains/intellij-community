f = "fst"
s = "snd"
"first is %(fst)s, second is %(snd)s" % {f: 1, s: 2}

snd = "snd"
"%(f)s %(snd)s" % {"f": 2, snd: 2}