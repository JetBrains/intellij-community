//file
package switch_demo;

public class SwitchDemo {
    public static void main(String[] args) {
        int month = 8;
        String monthString;
        int a = switch (month) {
            case 1:  monthString = "January";        yield 1;
            case 2:  monthString = "February";       yield 2;
            case 3:  monthString = "March";          yield 3;
            case 4:  monthString = "April";          yield 4;
            case 5:  monthString = "May";            yield 5;
            case 6:  monthString = "June";           yield 6;
            case 7:  monthString = "July";           yield 7;
            case 8:  monthString = "August";         yield 8;
            case 9:  monthString = "September";      yield 9;
            case 10: monthString = "October";        yield 10;
            case 11: monthString = "November";       yield 11;
            case 12: monthString = "December";       yield 12;
            default: monthString = "Invalid month";  yield 13;
        }
        System.out.println(monthString);
    }
}