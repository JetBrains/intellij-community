KWARGS = {
    "do_stuff": True,
    "little_list": ['WORLD_RET_BP_IMPALA_AB.Control', 'WORLD_RET_BP_IMPALA_AB.Impala_WS'],
}

for element in <warning descr="Expected type 'collections.Iterable', got 'Union[bool, List[str]]' instead">KWARGS["little_list"]</warning>:
    print(element)