class InfiniteZeroSequence:
    def __getitem__(self, i):
        return 0


for x in InfiniteZeroSequence():
    # <ref>
    pass
