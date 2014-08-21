package ca.corefacility.bioinformatics.irida.model;

import java.util.Date;

/**
 * An object with a timestamp
 * 
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 *
 */
public interface Timestamped {

	/**
	 * Get the created date of the object
	 * 
	 * @returnA {@link Date} object of the created date
	 */
	public Date getCreatedDate();

	/**
	 * Get the date that this object was last modified
	 * 
	 * @return {@link Date} object of the modified date
	 */
	public Date getModifiedDate();

	/**
	 * Set the modification time of this object
	 * 
	 * @param modifiedDate
	 *            The date where this object was modified
	 */
	public void setModifiedDate(Date modifiedDate);
}
