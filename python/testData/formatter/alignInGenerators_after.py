def supprice():
    if True:
        if True:
            agdrn = sum(VARS[drn + price] * md.c[drn][1] * md.c[drn][3] *
                        exp(md.c[drn][2] * VARS['SEEPAGE'] - md.c[drn][3] * pmp)
                        for drn in md.agdrn_nodes if drn in md.c)
