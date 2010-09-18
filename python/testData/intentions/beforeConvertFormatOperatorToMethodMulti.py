n = 8
a = "%(n)d bottles of %(what)s on the %(where)s"\
    """
    %(n)u bottles
    of %(what)s
    """\
    r'\n/ take %(howmuch)r down \n/'\
    ur"pass it %(how)8s"\
    "%(new_n)#i {that is, %(percent)+3.2f%% less} "\
    "bottles of %(what)6s on the %(where)s"\
    <caret>%\
    dict(n=n, where="wall", howmuch=u'one', how="'round\t", new_n=(n-1), percent=100*(1-((n-1.0)/n)),  what="beer")
print a
