package ch.ethz.rse;

/**
 * We are verifying calls into this class
 * 
 */
public final class Frog {

	// total profit made ever
	public static int total_profit = 0;
  
	// Production cost of the item
	private final int production_cost;
  
	public Frog(int production_cost) {
	  this.production_cost = production_cost;
	}
  
	public void sell(int price) {
	   // check NON_NEGATIVE
	  assert 0 <= price;
  
	  // check ITEM_PROFIT
	  assert this.production_cost <= price;
	  Frog.total_profit += (price - this.production_cost);
  
	  // check OVERALL_PROFIT
	  // (check only upon program termination)
	  // assert Frog.total_profit >= 0;
	}
  }