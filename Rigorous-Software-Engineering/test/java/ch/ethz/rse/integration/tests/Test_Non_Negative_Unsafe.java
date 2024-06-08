package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Test_Non_Negative_Unsafe {
  
    public void m() {
      Frog frog_with_glasses = new Frog(5);
      frog_with_glasses.sell(10);
      frog_with_glasses.sell(-3);
    }
}
