from series import Series


class NDFrame:
    ...


class DataFrame:
    def __getitem__(self, key):
        if key == "1":
            return None
        if key == "2":
            return Series()
        if key == "3":
            return NDFrame()
        if key == "4":
            return DataFrame()

