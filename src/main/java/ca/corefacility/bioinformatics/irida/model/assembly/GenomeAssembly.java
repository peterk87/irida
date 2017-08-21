package ca.corefacility.bioinformatics.irida.model.assembly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;

import org.springframework.data.annotation.CreatedDate;

import ca.corefacility.bioinformatics.irida.model.IridaResourceSupport;
import ca.corefacility.bioinformatics.irida.model.MutableIridaThing;

/**
 * Defines a genome assembly which can be associated with a sample.
 */
@Entity
@Table(name = "genome_assembly")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class GenomeAssembly extends IridaResourceSupport implements MutableIridaThing {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@NotNull
	@CreatedDate
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "created_date")
	private Date createdDate;

	protected GenomeAssembly() {
		this.id = null;
		this.createdDate = null;
	}

	public GenomeAssembly(Date createdDate) {
		this.createdDate = createdDate;
	}

	@Override
	public String getLabel() {
		return getFile().getFileName().toString();
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public Date getCreatedDate() {
		return createdDate;
	}

	@Override
	public void setId(Long id) {
		this.id = id;
	}

	@Override
	public Date getModifiedDate() {
		return createdDate;
	}

	@Override
	public void setModifiedDate(Date modifiedDate) {
		throw new UnsupportedOperationException("Cannot update a genome assembly");
	}

	public long getFileSize() throws IOException {
		return Files.size(getFile());
	}
	
	/**
	 * Gets the assembly file.
	 * 
	 * @return The assembly file.
	 */
	public abstract Path getFile();

	@Override
	public int hashCode() {
		return Objects.hash(id, createdDate);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		GenomeAssembly other = (GenomeAssembly) obj;
		return Objects.equals(this.id, other.id) && Objects.equals(this.createdDate, other.createdDate);
	}
}