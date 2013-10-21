import datetime

class MyDate(datetime.date):
    def __init__(self, year, month, day):
        <selection>super(MyDate, self).__init__(year, month, day)</selection>

