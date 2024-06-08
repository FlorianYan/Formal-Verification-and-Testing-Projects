package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;
// expected results:
// NON_NEGATIVE SAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Test_Non_Negative_Loop_Safe {
    public void m() {
      Frog frog_with_glasses = new Frog(2);
      for(int i = 0; i < 20; i++){
        frog_with_glasses.sell(i);
      }
    }
}
