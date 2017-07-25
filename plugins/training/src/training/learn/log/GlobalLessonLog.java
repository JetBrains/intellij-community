package training.learn.log;

import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Tag;
import training.learn.Lesson;

import java.util.HashMap;

/**
 * Created by karashevich on 13/10/15.
 */
@Tag("GlobalLessonLog")
public class GlobalLessonLog {

    public HashMap<String, SmartList<LessonLog>> globalLessonLogMap; //map lesson -> lessonLog

    public GlobalLessonLog() {
        globalLessonLogMap = new HashMap<String, SmartList<LessonLog>>();
    }

    public void commitSession(Lesson lesson) {

        if (lesson.getName() == null) return;

        if (globalLessonLogMap.containsKey(lesson.getName())) {
            final SmartList<LessonLog> sessions = globalLessonLogMap.get(lesson.getName());
            if (sessions.size() > 0) {
                final int lastIndex = sessions.size() - 1;
                final LessonLog lastSession = sessions.get(lastIndex);
                if (lastSession.getFirstDate().equals(lesson.getLessonLog().getFirstDate())) {
                    //concatenate session
                    sessions.set(lastIndex, lesson.getLessonLog());
                } else {
                    globalLessonLogMap.get(lesson.getName()).add(lesson.getLessonLog());
                }
            } else {
                globalLessonLogMap.get(lesson.getName()).add(lesson.getLessonLog());
            }
        } else {
            final SmartList<LessonLog> sessions = new SmartList<LessonLog>();
            sessions.add(lesson.getLessonLog());
            globalLessonLogMap.put(lesson.getName(), sessions);
        }
    }
}
