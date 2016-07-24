import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class JBossLogTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new JBossLogTest().logSomething();
    }
}
