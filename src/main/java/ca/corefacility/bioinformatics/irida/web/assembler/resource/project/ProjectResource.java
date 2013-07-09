package ca.corefacility.bioinformatics.irida.web.assembler.resource.project;

import ca.corefacility.bioinformatics.irida.model.Project;
import ca.corefacility.bioinformatics.irida.model.roles.impl.Identifier;
import ca.corefacility.bioinformatics.irida.web.assembler.resource.Resource;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A resource for {@link Project}s.
 *
 * @author Franklin Bristow <franklin.bristow@phac-aspc.gc.ca>
 */
@XmlRootElement(name = "project")
public class ProjectResource extends Resource<Identifier, Project> {

    public ProjectResource() {
        super(new Project());
    }

    public ProjectResource(Project project) {
        super(project);
    }

    @XmlElement
    public String getName() {
        return resource.getName();
    }

    public void setName(String name) {
        this.resource.setName(name);
    }
}
