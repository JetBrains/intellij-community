package training.learn.log;

import training.learn.Lesson;

import java.util.ArrayList;
import java.util.Date;

/**
 * Created by karashevich on 13/10/15.
 */
public class LessonLog {

    public Lesson myLesson;
    public ArrayList<MyPair> logData;
    public int exerciseCount = 0;

    public LessonLog(){
        myLesson = null;
        logData = new ArrayList<MyPair>();
    }

    public Lesson getMyLesson() {
        return myLesson;
    }

    public void setMyLesson(Lesson myLesson) {
        this.myLesson = myLesson;
    }

    public LessonLog(Lesson lesson) {
        myLesson = lesson;
        logData = new ArrayList<MyPair>();
        log("Log is created. Lesson:" + lesson.getName());
    }

    public void log(String actionString){
        logData.add(new MyPair(new Date(), actionString));
    }

    public void resetCounter(){
        exerciseCount = 0;
    }

    public Date getFirstDate(){
        return logData.get(0).first;
    }

    public void print(){
        for (MyPair dateStringPair : logData) {
            System.out.println(dateStringPair.first + ": " + dateStringPair.first);
        }
    }

    public String exportToString(){
        StringBuilder sb = new StringBuilder();
        for (MyPair dateStringPair : logData) {
            sb.append(dateStringPair.first).append(": ").append(dateStringPair.first).append(";\n");
        }
        return sb.toString();
    }

}
