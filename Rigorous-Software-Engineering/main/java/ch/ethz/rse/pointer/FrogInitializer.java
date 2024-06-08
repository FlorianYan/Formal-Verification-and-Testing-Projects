package ch.ethz.rse.pointer;

import soot.jimple.internal.JInvokeStmt;

/**
 * 
 * Contains information about the initializer of a Frog object
 *
 */
public class FrogInitializer {

	/**
	 * statement that performs the initialization
	 */
	private final JInvokeStmt statement;

	/**
	 * Unique identifier of the initializer
	 */
	private final int uniqueNumber;

	/**
	 * argument in the constructor
	 */
	public final int argument;


	/**
	 * 
	 * @param statement    piece of code running the initializer
	 * @param uniqueNumber unique identifier of the initializer
	 * @param argment      argument in the constructor
	 */
	public FrogInitializer(JInvokeStmt statement, int uniqueNumber, int argument) {
		this.statement = statement;
		this.uniqueNumber = uniqueNumber;
		this.argument = argument;
	}

	/**
	 * 
	 * @return piece of code running the initializer
	 */
	public JInvokeStmt getStatement() {
		return statement;
	}

	/**
	 * 
	 * @return unique identifier of the initializer
	 */
	private int getUniqueNumber() {
		return this.uniqueNumber;
	}

	/**
	 * 
	 * @return unique label of this initializer
	 */
	public String getUniqueLabel() {
		return "AbstractObject" + this.getUniqueNumber() + ".end";
	}

	public String toString() {
		return "AbstractObject" + this.getUniqueNumber();
	}

}