package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE SAFE
// ITEM_PROFIT SAFE
// OVERALL_PROFIT SAFE

public class Basic_Test_Safe {

  public static void m1() {
    Frog frog_with_hat = new Frog(4);
    frog_with_hat.sell(5);
    frog_with_hat.sell(6);

    Frog frog_with_pants = new Frog(2);
    frog_with_pants.sell(2);
  }
}