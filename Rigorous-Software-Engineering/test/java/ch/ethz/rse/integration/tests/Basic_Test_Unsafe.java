package ch.ethz.rse.integration.tests;

import ch.ethz.rse.Frog;

// expected results:
// NON_NEGATIVE UNSAFE
// ITEM_PROFIT UNSAFE
// OVERALL_PROFIT UNSAFE

public class Basic_Test_Unsafe {

	public void m2(int j) {
	  Frog frog_with_sweater = new Frog(2);
	  if(-1 <= j && j <= 3)
		frog_with_sweater.sell(j);
	}
  }
