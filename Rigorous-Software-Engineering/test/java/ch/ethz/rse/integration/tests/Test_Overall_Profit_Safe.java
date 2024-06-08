package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;
// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT SAFE

public class Test_Overall_Profit_Safe {
    public void m(){
        Frog f = new Frog(1);
        for(int i = 1; i > 0; i++){
            f.sell(-1);
        }
    }
}
