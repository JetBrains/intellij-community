class Test {
    public String test(int n) {
        try {
            return "success";
        } catch (Exception e){
            return "failure";
        } finally{
            System.out.println("tried running script");
        }
    }
}
