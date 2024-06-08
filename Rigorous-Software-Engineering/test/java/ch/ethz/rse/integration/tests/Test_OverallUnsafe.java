package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE SAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Test_OverallUnsafe {
    public static void m1() {
    Frog frog_with_hat = new Frog(4);
    frog_with_hat.sell(3);
  }
}
