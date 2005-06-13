public class YoYo {
    int y;
    static class YoYoYo {
        private YoYo anObject;
        private int y;

        public YoYoYo(YoYo anObject, int y) {
            this.anObject = anObject;
            this.y = y;
        }

        void foo (){
            YoYo yoYoy = anObject;
            int t = y;
            int t1 = yoYoy.y;
        }
    }
}

