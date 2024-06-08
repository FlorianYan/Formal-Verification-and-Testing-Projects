package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT SAFE

public class Basic_Test_OverallSafe {

    public void m() {
      Frog frog_with_socks = new Frog(2);
      frog_with_socks.sell(-1);
      frog_with_socks.sell(6);
    }
  }