package ch.ethz.rse.utils;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Loads properties from the properties file. Needed to provide configuration
 * details.
 *
 */
public class Configuration {

	private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

	/**
	 * Publicly available singleton
	 */
	public static Configuration props = new Configuration();

	/**
	 * File to load properties from
	 */
	private final String propertiesFile = "properties.config";

	/**
	 * Properties loaded from {@link #propertiesFile}
	 */
	private final Properties prop = new Properties();

	private Configuration() {
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFile);
		if (is == null) {
			logger.error("Error loading {}: File not present", propertiesFile);
			throw new RuntimeException("File not found:" + propertiesFile);
		}
		try {
			this.prop.load(is);
		} catch (Exception e) {
			logger.error("Error loading {}:{}", propertiesFile, e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * @return java home directory to use in soot
	 */
	public String getSootJavaHome() {
		return this.prop.getProperty("SOOT_JAVA_HOME");
	}

	/**
	 * 
	 * @return directory containing the java sources
	 */
	public String getBasedir() {
		return this.prop.getProperty("BASEDIR");
	}

}
