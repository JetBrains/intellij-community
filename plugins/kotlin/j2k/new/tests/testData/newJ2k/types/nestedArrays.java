class A {
    public static int [][] sampleFunction(int a[][], int b[][]) {
        int n = a[0].length;
        int m = a.length;
        int p = b[0].length;
        int result[][] = new int[m][p];
        for (int i=0; i<m; i++){
            for(int j=0; j<p; j++){
                for (int k=0; k<n ; k++){
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }
}