package ca.corefacility.bioinformatics.irida.model.joins.impl;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.Audited;

import ca.corefacility.bioinformatics.irida.model.joins.Join;
import ca.corefacility.bioinformatics.irida.model.project.Project;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;

/**
 * Relationship between two {@link Project}s. A {@link Project} can have a list
 * of {@link RelatedProjectJoin}s where it will search for {@link Sample}s.
 * 
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 *
 */
@Entity
@Table(name = "related_project", uniqueConstraints = @UniqueConstraint(columnNames = { "subject_id",
		"relatedProject_id" }))
@Audited
public class RelatedProjectJoin implements Join<Project, Project> {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.DETACH)
	@JoinColumn(name = "subject_id")
	@NotNull
	private Project subject;

	@ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.DETACH)
	@JoinColumn(name = "relatedProject_id")
	@NotNull
	private Project relatedProject;

	private Date modifiedDate;

	@NotNull
	private Date createdDate;

	public RelatedProjectJoin() {
		createdDate = new Date();
		modifiedDate = createdDate;
	}

	public RelatedProjectJoin(Project subject, Project object) {
		this();
		this.subject = subject;
		this.relatedProject = object;
	}

	@Override
	public String getLabel() {
		return "Related project: " + subject.getLabel() + " => " + relatedProject.getLabel();
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public Date getModifiedDate() {
		return modifiedDate;
	}

	@Override
	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;

	}

	@Override
	public Project getSubject() {
		return subject;
	}

	@Override
	public void setSubject(Project subject) {
		this.subject = subject;
	}

	@Override
	public Project getObject() {
		return relatedProject;
	}

	@Override
	public void setObject(Project object) {
		this.relatedProject = object;
	}

	@Override
	public Date getTimestamp() {
		return getCreatedDate();
	}

	@Override
	public Date getCreatedDate() {
		return createdDate;
	}

}