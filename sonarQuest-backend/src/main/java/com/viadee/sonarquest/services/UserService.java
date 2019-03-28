package com.viadee.sonarquest.services;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.viadee.sonarquest.entities.Level;
import com.viadee.sonarquest.entities.Permission;
import com.viadee.sonarquest.entities.Role;
import com.viadee.sonarquest.entities.RoleName;
import com.viadee.sonarquest.entities.User;
import com.viadee.sonarquest.entities.UserToWorldDto;
import com.viadee.sonarquest.entities.World;
import com.viadee.sonarquest.repositories.UserRepository;
import com.viadee.sonarquest.repositories.WorldRepository;

@Service
public class UserService implements UserDetailsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

	@Autowired
	private RoleService roleService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WorldService worldService;

	@Autowired
	private LevelService levelService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private WorldRepository worldRepository;

	@Override
	public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
		final User user = findByUsername(username);
		final Set<Permission> permissions = permissionService.getAccessPermissions(user);

		final List<SimpleGrantedAuthority> authoritys = permissions.stream()
				.map(berechtigung -> new SimpleGrantedAuthority(berechtigung.getPermission()))
				.collect(Collectors.toList());

		return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), true,
				true, true, true, authoritys);
	}

	public User findByUsername(final String username) {
		return userRepository.findByUsername(username);
	}

	private User findById(final Long id) {
		return userRepository.findOne(id);
	}

	public World updateUsersCurrentWorld(final User user, final Long worldId) {
		final World world = worldService.findById(worldId);
		user.setCurrentWorld(world);
		userRepository.saveAndFlush(user);
		return user.getCurrentWorld();
	}

	@Transactional
	public synchronized User save(final User user) {
		User toBeSaved = null;
		final String username = user.getUsername();
		final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		if (user.getId() == null) {
			// Only the password hash needs to be saved
			final String password = encoder.encode(user.getPassword());
			final Role role = user.getRole();
			final RoleName roleName = role.getName();
			final Role userRole = roleService.findByName(roleName);
			toBeSaved = usernameFree(username) ? user : null;
			if (toBeSaved != null) {
				toBeSaved.setPassword(password);
				toBeSaved.setRole(userRole);
				toBeSaved.setCurrentWorld(user.getCurrentWorld());
				toBeSaved.setGold(0l);
				toBeSaved.setXp(0l);
				toBeSaved.setLevel(levelService.getLevelByUserXp(0l));
			}
		} else {
			toBeSaved = findById(user.getId());
			if (toBeSaved != null) {
				final Role role = user.getRole();
				final RoleName roleName = role.getName();
				final Role userRole = roleService.findByName(roleName);
				setUsername(toBeSaved, username);
				setPassword(user, toBeSaved, encoder);
				toBeSaved.setRole(userRole);
				toBeSaved.setAboutMe(user.getAboutMe());
				toBeSaved.setPicture(user.getPicture());
				toBeSaved.setCurrentWorld(user.getCurrentWorld());
				toBeSaved.setWorlds(user.getWorlds());
				toBeSaved.setGold(user.getGold());
				toBeSaved.setXp(user.getXp());
				toBeSaved.setLevel(levelService.getLevelByUserXp(user.getXp()));
				toBeSaved.setAdventures(user.getAdventures());
				toBeSaved.setArtefacts(user.getArtefacts());
				toBeSaved.setAvatarClass(user.getAvatarClass());
				toBeSaved.setParticipations(user.getParticipations());
				toBeSaved.setUiDesign(user.getUiDesign());
			}
		}

		return toBeSaved != null ? userRepository.saveAndFlush(toBeSaved) : null;
	}

	private void setUsername(User toBeSaved, final String username) {
		if (!username.equals(toBeSaved.getUsername()) && usernameFree(username)) {
			toBeSaved.setUsername(username);
		}
	}

	private void setPassword(final User user, User toBeSaved, final BCryptPasswordEncoder encoder) {
		// if there are identical hashes in the pw fields, do not touch them
		if (!toBeSaved.getPassword().equals(user.getPassword())) {
			// change password only if it differs from the old one
			final String oldPassHash = toBeSaved.getPassword();
			if (!oldPassHash.equals(user.getPassword()) || !encoder.matches(user.getPassword(), oldPassHash)) {
				final String password = encoder.encode(user.getPassword());
				toBeSaved.setPassword(password);
				LOGGER.info("The password for user " + user.getUsername() + " (id: " + user.getId()
						+ ") has been changed.");
			}
		}
	}

	private boolean usernameFree(final String username) {
		return userRepository.findByUsername(username) == null;
	}

	public void delete(final Long userId) {
		final User user = findById(userId);
		userRepository.delete(user);
	}

	public User findById(final long userId) {
		return userRepository.findOne(userId);
	}

	public List<User> findAll() {
		return userRepository.findAll();
	}

	public List<User> findByRole(final RoleName roleName) {
		return findAll().stream().filter(user -> user.getRole().getName() == roleName).collect(Collectors.toList());
	}

	public Level getLevel(final long xp) {
		return levelService.getLevelByUserXp(xp);
	}

	@Transactional
	public void updateLastLogin(final String username) {
		final User user = findByUsername(username);
		user.setLastLogin(Timestamp.valueOf(LocalDateTime.now()));
		save(user);
	}

	public Boolean updateUserToWorld(List<UserToWorldDto> userToWorlds) {

		userToWorlds.forEach(userToWorld -> {
			User user = userRepository.findOne(userToWorld.getUserId());
			if (userToWorld.getJoined()) {
				user.addWorld(worldRepository.findOne(userToWorld.getWorldId()));
			} else {
				user.removeWorld(worldRepository.findOne(userToWorld.getWorldId()));
			}

			userRepository.save(user);
		});

		return true;
	}

	public UserRepository getUserRepository() {
		return userRepository;
	}

	public void setUserRepository(UserRepository userRepository) {
		this.userRepository = userRepository;
	}
}
