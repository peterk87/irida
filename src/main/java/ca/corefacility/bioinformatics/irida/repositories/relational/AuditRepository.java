
package ca.corefacility.bioinformatics.irida.repositories.relational;

import ca.corefacility.bioinformatics.irida.model.IridaThing;
import ca.corefacility.bioinformatics.irida.repositories.relational.auditing.UserRevEntity;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.stereotype.Repository;
 
/**
 * A repository for accessing previous versions of audited entities.
 * @author Thomas Matthews <thomas.matthews@phac-aspc.gc.ca>
 */
@Repository
public class AuditRepository {
    
    private SessionFactory sessionFactory;
    
    public AuditRepository(){}
    
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
        
    public <Type extends IridaThing> Type getVersion(Long id, Integer revision, Class<Type> classType){
        Session ses = sessionFactory.openSession();

        AuditReader get = AuditReaderFactory.get(ses);
        
        Type find = (Type) get.find(classType, id, revision);
        ses.close();
        
        return find;
    }
    
    public List<UserRevEntity> getRevisions(Long id, Class classType){
        Session ses = sessionFactory.openSession();
        AuditReader get = AuditReaderFactory.get(ses);
        
        List<Number> revisions = get.getRevisions(classType, id);
        
        Criteria crit = ses.createCriteria(UserRevEntity.class).add(Restrictions.in("id", revisions));
        List<UserRevEntity> list = crit.list();
        
        ses.close();
        
        return list;
    }
    
}