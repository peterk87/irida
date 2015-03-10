package ca.corefacility.bioinformatics.irida.security.permissions;

import java.io.Serializable;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.security.core.Authentication;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.model.user.Role;

/**
 * Generic super-class for permission types to extend from.
 * 
 * 
 * @param <DomainObjectType>
 *            the type of domain object that this permission is evaluating.
 */
public abstract class BasePermission<DomainObjectType, IdentifierType extends Serializable> {

	private static final Logger logger = LoggerFactory.getLogger(BasePermission.class);

	private static final String ADMIN_AUTHORITY = Role.ROLE_ADMIN.getAuthority();

	/**
	 * Get the implementation-specific permission provided.
	 * 
	 * @return the permission provided by the permission class.
	 */
	public abstract String getPermissionProvided();

	/**
	 * This method is called by {@link BasePermission} to evaluate the custom
	 * permissions provided by implementing classes.
	 * 
	 * @param authentication
	 *            the authenticated user.
	 * @param targetDomainObject
	 *            the object that the user is attempting to access.
	 * @return true if permitted, false otherwise.
	 */
	protected abstract boolean customPermissionAllowed(Authentication authentication,
			DomainObjectType targetDomainObject);

	/**
	 * The type of object to be loaded from the database.
	 */
	private Class<DomainObjectType> domainObjectType;

	/**
	 * The type of identifier used to load this object
	 */
	private Class<IdentifierType> identifierType;

	/**
	 * The repository to load objects with.
	 */
	private CrudRepository<DomainObjectType, IdentifierType> repository;

	/**
	 * Constructor with handles on the type of repository and type of domain
	 * object.
	 * 
	 * @param domainObjectType
	 *            the domain object type managed by this permission.
	 * @param repositoryId
	 *            the identifier of the repository to load from the spring
	 *            application context.
	 */
	protected BasePermission(Class<DomainObjectType> domainObjectType, Class<IdentifierType> identifierType,
			CrudRepository<DomainObjectType, IdentifierType> repository) {
		this.repository = repository;
		this.domainObjectType = domainObjectType;
		this.identifierType = identifierType;
	}

	/**
	 * Evaluates the permission of a single object.
	 * 
	 * @param authentication
	 *            The Authentication object.
	 * @param targetDomainObject
	 *            The target domain object to evaluate permission (assumes this
	 *            is not a collection).
	 * @return True if permission is allowed on this object, false otherwise.
	 * @throws EntityNotFoundException
	 *             If the object does not exist.
	 */
	@SuppressWarnings("unchecked")
	private boolean customPermissionAllowedSingleObject(Authentication authentication, Object targetDomainObject) {
		DomainObjectType domainObject;

		if (identifierType.isAssignableFrom(targetDomainObject.getClass())) {
			logger.trace("Trying to find domain object by id [" + targetDomainObject + "]");
			domainObject = repository.findOne((IdentifierType) targetDomainObject);
			if (domainObject == null) {
				throw new EntityNotFoundException("Could not find entity with id [" + targetDomainObject + "]");
			}
		} else if (domainObjectType.isAssignableFrom(targetDomainObject.getClass())) {
			// reflection replacement for instanceof
			domainObject = (DomainObjectType) targetDomainObject;
		} else {
			throw new IllegalArgumentException("Parameter to " + getClass().getName() + " must be of type Long or "
					+ domainObjectType.getName() + ".");
		}

		return customPermissionAllowed(authentication, domainObject);
	}

	/**
	 * Tests permission for a collection of objects.
	 * 
	 * @param authentication
	 *            The Authentication object.
	 * @param targetDomainObjects
	 *            The collection of domain objects to check for permission.
	 * @return True if permission is allowed for every object in the collection,
	 *         false otherwise.
	 * @throws EntityNotFoundException
	 *             If one of the objects in the collection does not exist.
	 */
	private boolean customPermissionAllowedCollection(Authentication authentication, Collection<?> targetDomainObjects) {
		boolean permitted = true;
		for (Object domainObjectInCollection : targetDomainObjects) {
			permitted &= customPermissionAllowedSingleObject(authentication, domainObjectInCollection);
		}

		return permitted;
	}

	/**
	 * Is the authenticated user allowed to perform some action on the target
	 * domain object?
	 * 
	 * @param authentication
	 *            the authenticated user.
	 * @param targetDomainObject
	 *            the object the user is requesting to perform an action on.
	 * @return true if the action is allowed, false otherwise.
	 */
	public final boolean isAllowed(Authentication authentication, Object targetDomainObject) {
		// fast pass for administrators -- administrators are allowed to access
		// everything.
		if (authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals(ADMIN_AUTHORITY))) {
			return true;
		}

		if (targetDomainObject instanceof Collection<?>) {
			return customPermissionAllowedCollection(authentication, (Collection<?>) targetDomainObject);
		} else {
			return customPermissionAllowedSingleObject(authentication, targetDomainObject);
		}
	}
}
