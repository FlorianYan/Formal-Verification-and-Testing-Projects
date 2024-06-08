package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;
// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Test_If_Unsafe {
    public void m(int a, int b){
        Frog f = new Frog(5);
        int x = a*b;
        if(a != 0){
            f.sell(x);
        }
    }
}
