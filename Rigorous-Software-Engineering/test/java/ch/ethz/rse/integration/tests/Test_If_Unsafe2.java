package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;
// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Test_If_Unsafe2 {
    public void m(int a){
        Frog f1 = new Frog(10);
        Frog f2 = new Frog(4);

        if(a >= 0){
            if(a > 0){
                if(a <= 4){
                    f1.sell(0);
                }
                f2.sell(1);
            }
            if(a == 0){
                f1.sell(0);
            }
            f2.sell(3);
        }
        else{
            if(a < -2){
                f1.sell(0);
            }
            if(a != -1){
                f1.sell(0);
            }
            f2.sell(a);
        }
    }
}
