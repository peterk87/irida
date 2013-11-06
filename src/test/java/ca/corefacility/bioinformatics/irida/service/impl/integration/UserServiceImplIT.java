package ca.corefacility.bioinformatics.irida.service.impl.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ca.corefacility.bioinformatics.irida.config.IridaApiServicesConfig;
import ca.corefacility.bioinformatics.irida.config.data.IridaApiTestDataSourceConfig;
import ca.corefacility.bioinformatics.irida.config.data.jpa.TestJpaProperties;
import ca.corefacility.bioinformatics.irida.exceptions.EntityExistsException;
import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.model.Project;
import ca.corefacility.bioinformatics.irida.model.Role;
import ca.corefacility.bioinformatics.irida.model.User;
import ca.corefacility.bioinformatics.irida.model.joins.Join;
import ca.corefacility.bioinformatics.irida.service.ProjectService;
import ca.corefacility.bioinformatics.irida.service.UserService;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = { IridaApiServicesConfig.class,
		IridaApiTestDataSourceConfig.class, TestJpaProperties.class })
@ActiveProfiles("test")
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class })
public class UserServiceImplIT {

	@Autowired
	private UserService userService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Before
	public void setUp() {
		User u = new User();
		u.setUsername("fbristow");
		u.setPassword(passwordEncoder.encode("Password1"));
		u.setSystemRole(Role.ROLE_MANAGER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(u, "Password1",
				ImmutableList.of(Role.ROLE_MANAGER));
		auth.setDetails(u);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@Test(expected = AccessDeniedException.class)
	public void testCreateManagerAsManagerFail() {
		User u = new User();
		u.setSystemRole(Role.ROLE_MANAGER);

		userService.create(u);
	}

	@Test(expected = AccessDeniedException.class)
	public void testCreateAdministratorAsManagerFail() {
		User u = new User();
		u.setSystemRole(Role.ROLE_ADMIN);

		userService.create(u);
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testCreateUserAsManagerSucceed() {
		User u = new User("user", "user@user.us", "Password1", "User", "User", "7029");
		u.setSystemRole(Role.ROLE_USER);
		userService.create(u);
	}

	@Test(expected = AccessDeniedException.class)
	public void testCreateUserAsUserFail() {
		asUser().userService.create(new User());
	}

	@Test(expected = AccessDeniedException.class)
	public void testUpdateToAdministratorAsManagerFail() {
		Map<String, Object> properties = ImmutableMap.of("systemRole", (Object) Role.ROLE_ADMIN);
		userService.update(1L, properties);
	}

	@Test(expected = AccessDeniedException.class)
	public void testUpdateToManagerAsManagerFail() {
		Map<String, Object> properties = ImmutableMap.of("systemRole", (Object) Role.ROLE_MANAGER);
		userService.update(1L, properties);
	}

	@Test(expected = AccessDeniedException.class)
	public void testUpdateToAdminAsUserFail() {
		Map<String, Object> properties = ImmutableMap.of("systemRole", (Object) Role.ROLE_ADMIN);
		asUser().userService.update(1L, properties);
	}

	@Test(expected = AccessDeniedException.class)
	public void testUpdateToManagerAsUserFail() {
		Map<String, Object> properties = ImmutableMap.of("systemRole", (Object) Role.ROLE_MANAGER);
		asUser().userService.update(1L, properties);
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testUpdateUserAsManagerSucceed() {
		String updatedPhoneNumber = "123-4567";
		Map<String, Object> properties = ImmutableMap.of("phoneNumber", (Object) updatedPhoneNumber);
		User updated = userService.update(1L, properties);
		assertEquals("Phone number should be updated.", updatedPhoneNumber, updated.getPhoneNumber());
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testUpdateOwnAccountSucceed() {
		String updatedPhoneNumber = "456-7890";
		Map<String, Object> properties = ImmutableMap.of("phoneNumber", (Object) updatedPhoneNumber);
		User updated = userService.update(1L, properties);
		assertEquals("Phone number should be updated.", updatedPhoneNumber, updated.getPhoneNumber());
	}

	@Test(expected = AuthenticationCredentialsNotFoundException.class)
	public void testUpdatePasswordWithoutLoginDetails() {
		SecurityContextHolder.clearContext();
		userService.changePassword(1l, "any password");
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testUpdatePasswordWithCompleteLoginDetails() {
		String updatedPassword = "NewPassword1";
		User updated = userService.changePassword(1l, updatedPassword);
		assertNotEquals("Password in user object should be encoded.", updated.getPassword(), updatedPassword);
		assertTrue("Password is encoded correctly.", passwordEncoder.matches(updatedPassword, updated.getPassword()));
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testUpdatePasswordWithExpiredPassword() {
		((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).setCredentialsNonExpired(false);
		SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
		String updatedPassword = "NewPassword1";
		User updated = userService.changePassword(1l, updatedPassword);
		assertNotEquals("Password in user object should be encoded.", updated.getPassword(), updatedPassword);
		assertTrue("User should not have expired credentials anymore.", updated.isCredentialsNonExpired());
		assertTrue("Password is encoded correctly.", passwordEncoder.matches(updatedPassword, updated.getPassword()));
	}

	@Test(expected = AccessDeniedException.class)
	public void testUpdatePasswordWithAnonymousUser() {
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymouse", ImmutableList.of(Role.ROLE_CLIENT)));
		userService.changePassword(1l, "NewPassword1");
	}

	@Test(expected = EntityExistsException.class)
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testCreateDuplicateEmail() {
		User u = new User("user", "manager@nowhere.com", "Password1", "User", "User", "7029");
		u.setSystemRole(Role.ROLE_USER);
		userService.create(u);
	}

	@Test(expected = EntityExistsException.class)
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testCreateDuplicateUsername() {
		User u = new User("fbristow", "distinct@nowhere.com", "Password1", "User", "User", "7029");
		u.setSystemRole(Role.ROLE_USER);
		userService.create(u);
	}

	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testGetUserByUsername() {
		String username = "fbristow";
		User u = userService.getUserByUsername(username);
		assertEquals("Username is wrong.", username, u.getUsername());
	}
	
	@Test(expected = EntityNotFoundException.class)
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testGetUserByInvalidUsername() {
		String username = "random garbage";
		userService.getUserByUsername(username);
	}
	
	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testGetUsersForProject() {
		Project p = projectService.read(1L);
		Collection<Join<Project, User>> projectUsers = userService.getUsersForProject(p);
		assertEquals("Wrong number of users.", 1, projectUsers.size());
		Join<Project, User> projectUser = projectUsers.iterator().next();
		assertEquals("Wrong project.", p, projectUser.getSubject());
		assertEquals("Wrong user.", "fbristow", projectUser.getObject().getUsername());
	}
	
	@Test
	@DatabaseSetup("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	@DatabaseTearDown("/ca/corefacility/bioinformatics/irida/service/impl/UserServiceImplIT.xml")
	public void testGetUsersAvailableForProject() {
		Project p = projectService.read(1L);
		List<User> usersAvailableForProject = userService.getUsersAvailableForProject(p);
		assertEquals("Wrong number of users.", 1, usersAvailableForProject.size());
		User availableUser = usersAvailableForProject.iterator().next();
		assertEquals("Wrong user.", "differentUser", availableUser.getUsername());
	}

	private UserServiceImplIT asUser() {
		User u = new User();
		u.setUsername("fbristow");
		u.setPassword(passwordEncoder.encode("Password1"));
		u.setSystemRole(Role.ROLE_USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(u, "Password1",
				ImmutableList.of(Role.ROLE_USER));
		auth.setDetails(u);
		SecurityContextHolder.getContext().setAuthentication(auth);
		return this;
	}
}