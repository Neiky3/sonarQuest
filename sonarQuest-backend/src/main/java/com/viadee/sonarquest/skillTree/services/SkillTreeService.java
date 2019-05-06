package com.viadee.sonarquest.skillTree.services;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.poi.ss.formula.functions.Count;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viadee.sonarquest.services.ArtefactService;
import com.viadee.sonarquest.skillTree.dto.skillTreeDiagram.SkillTreeDiagramDTO;
import com.viadee.sonarquest.skillTree.dto.skillTreeDiagram.SkillTreeLinksDTO;
import com.viadee.sonarquest.skillTree.dto.skillTreeDiagram.SkillTreeObjectDTO;
import com.viadee.sonarquest.skillTree.entities.SkillTreeUser;
import com.viadee.sonarquest.skillTree.entities.SonarRule;
import com.viadee.sonarquest.skillTree.entities.UserSkill;
import com.viadee.sonarquest.skillTree.entities.UserSkillGroup;
import com.viadee.sonarquest.skillTree.entities.UserSkillToSkillTreeUser;
import com.viadee.sonarquest.skillTree.repositories.SkillTreeUserRepository;
import com.viadee.sonarquest.skillTree.repositories.SonarRuleRepository;
import com.viadee.sonarquest.skillTree.repositories.UserSkillGroupRepository;
import com.viadee.sonarquest.skillTree.repositories.UserSkillRepository;
import com.viadee.sonarquest.skillTree.repositories.UserSkillToSkillTreeUserRepository;

import net.bytebuddy.asm.Advice.Return;

@Service
public class SkillTreeService {

	@Autowired
	private SkillTreeUserRepository skillTreeUserRepository;

	@Autowired
	public UserSkillGroupRepository userSkillGroupRepository;

	@Autowired
	private UserSkillRepository userSkillRepository;

	@Autowired
	private UserSkillService userSkillService;

	private static final Logger LOGGER = LoggerFactory.getLogger(ArtefactService.class);

	public SkillTreeDiagramDTO generateGroupSkillTree() {
		List<UserSkillGroup> userSkillGroups = userSkillGroupRepository.findAll();
		SkillTreeDiagramDTO skillTreeDiagramDTO = new SkillTreeDiagramDTO();

		for (UserSkillGroup userSkillGroup : userSkillGroups) {
			skillTreeDiagramDTO.addNode(new SkillTreeObjectDTO(String.valueOf(userSkillGroup.getId()),
					String.valueOf(userSkillGroup.getName()), userSkillGroup.getIcon()));
			for (UserSkillGroup followingUserSkillGroup : userSkillGroup.getFollowingUserSkillGroups()) {
				skillTreeDiagramDTO.addLine(new SkillTreeLinksDTO(String.valueOf(userSkillGroup.getId()),
						String.valueOf(followingUserSkillGroup.getId())));
			}
		}
		return skillTreeDiagramDTO;

	}

	@Transactional
	public SkillTreeDiagramDTO generateSkillTreeForUserByGroupID(Long id, String mail) {
		SkillTreeDiagramDTO skillTreeDiagramDTO = new SkillTreeDiagramDTO();
		if (mail == null || mail.isEmpty()) {
			List<UserSkill> userSkills = userSkillRepository.findUserSkillsByGroup(id);
			for (UserSkill userSkill : userSkills) {
				skillTreeDiagramDTO.addNode(this.buildSkillTreeObject(new UserSkillToSkillTreeUser(userSkill, 0)));
				for (UserSkill followingUserSkill : userSkill.getFollowingUserSkills()) {
					skillTreeDiagramDTO.addLine(new SkillTreeLinksDTO(String.valueOf(userSkill.getId()),
							String.valueOf(followingUserSkill.getId())));
				}
			}
		} else {
			SkillTreeUser user = skillTreeUserRepository.findByMail(mail);
			if (user == null) {
				LOGGER.info("User with mail: {}  does not exist yet - creating it...", mail);
				user = userSkillService.createSkillTreeUser(mail);
			}
			List<UserSkill> userSkills = userSkillRepository.findRootUserSkillByGroupID(id, true);
			for (UserSkill userSKill : userSkills) {
				SkillTreeObjectDTO object = this.buildSkillTreeObject(
						new UserSkillToSkillTreeUser(userSKill, getColorForRootObjectFromUserByGroupID(user, id), isGroupLearnedCompletlyFromUserByGroupID(user, id)));
				object.setLearnCoverage(calculateCoverage(this.getAmountOfLearndSkillsFromUserByGroupID(user, id),
						this.getAmountOfUserSkillsFromUserByGroupID(user, id)));
				skillTreeDiagramDTO.addNode(object);
				for (UserSkill followingUserSkill : userSKill.getFollowingUserSkills()) {
					skillTreeDiagramDTO.addLine(new SkillTreeLinksDTO(String.valueOf(userSKill.getId()),
							String.valueOf(followingUserSkill.getId())));
				}
			}
			for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : user.getUserSkillToSkillTreeUser()) {
				if (userSkillToSkillTreeUser.getUserSkill().getUserSkillGroup().getId().equals(id)) {
					SkillTreeObjectDTO object = this.buildSkillTreeObject(userSkillToSkillTreeUser);
					object.setLearnCoverage(calculateCoverage(Double.valueOf(userSkillToSkillTreeUser.getRepeats()),
							Double.valueOf(userSkillToSkillTreeUser.getUserSkill().getRequiredRepetitions())));
					skillTreeDiagramDTO.addNode(object);
					for (UserSkill followingUserSkill : userSkillToSkillTreeUser.getUserSkill()
							.getFollowingUserSkills()) {
						skillTreeDiagramDTO.addLine(
								new SkillTreeLinksDTO(String.valueOf(userSkillToSkillTreeUser.getUserSkill().getId()),
										String.valueOf(followingUserSkill.getId())));
					}
				}
			}

		}

		return skillTreeDiagramDTO;
	}

	private Timestamp isGroupLearnedCompletlyFromUserByGroupID(SkillTreeUser user, Long id) {
		int counter = 0;
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
				.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
				.collect(Collectors.toList());
		for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
			if (userSkillToSkillTreeUser.getLearnedOn() != null) {
				counter++;
			}
		}
		if(userSkillToUserByGroupID.size() == counter) {
			return new Timestamp(System.currentTimeMillis());
		}
		return null;
	}
	private Timestamp isGroupLearnedCompletlyFromTeamByGroupID(List<String> mails, Long id) {
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = null;
		int counter = 0;
		for (String mail : mails) {
			SkillTreeUser user = skillTreeUserRepository.findByMail(mail);
			userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
					.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
					.collect(Collectors.toList());
			for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
				if (userSkillToSkillTreeUser.getLearnedOn() != null) {
					counter++;
				}
			}
			
		}
		if(userSkillToUserByGroupID.size()*mails.size() == counter) {
			return new Timestamp(System.currentTimeMillis());
		}
				
		return null;
	}

	private int getColorForRootObjectFromUserByGroupID(SkillTreeUser user, Long id) {
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
				.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
				.collect(Collectors.toList());
		for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
			if (userSkillToSkillTreeUser.getLearnedOn() != null) {
				return 1;
			}
		}
		return 0;
	}

	private int getColorForRootObjectFromTeamByGroupID(List<String> mails, Long id) {
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = null;
		for (String mail : mails) {
			SkillTreeUser user = skillTreeUserRepository.findByMail(mail);
			userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
					.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
					.collect(Collectors.toList());
			for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
				if (userSkillToSkillTreeUser.getLearnedOn() != null) {
					return 1;
				}
			}
		}
		return 0;
	}

	private Double getAmountOfLearndSkillsFromUserByGroupID(SkillTreeUser user, Long id) {
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
				.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
				.collect(Collectors.toList());
		Double counter = 0.0;
		for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
			if (userSkillToSkillTreeUser.getLearnedOn() != null) {
				counter++;
			}
		}
		return counter;
	}
	private Double getAmountOfLearndSkillsFromTeamByGroupID(List<String> mails, Long id) {
		List<UserSkillToSkillTreeUser> userSkillToUserByGroupID = null;
		Double counter = 0.0;
		for (String mail : mails) {
			SkillTreeUser user = skillTreeUserRepository.findByMail(mail);
			userSkillToUserByGroupID = user.getUserSkillToSkillTreeUser().stream()
					.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
					.collect(Collectors.toList());
			
			for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : userSkillToUserByGroupID) {
				if (userSkillToSkillTreeUser.getLearnedOn() != null) {
					counter++;
				}
			}
		}
		return counter;
	}

	private Double getAmountOfUserSkillsFromUserByGroupID(SkillTreeUser user, Long id) {
		return Double.valueOf(user.getUserSkillToSkillTreeUser().stream()
				.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
				.collect(Collectors.toList()).size());
	}
	
	private Double getAmountOfUserSkillsFromTeamByGroupID(List<String> mails, Long id) {
		SkillTreeUser user = skillTreeUserRepository.findByMail(mails.get(0));
		return Double.valueOf(user.getUserSkillToSkillTreeUser().stream()
				.filter(userSkillToUser -> userSkillToUser.getUserSkill().getUserSkillGroup().getId() == id)
				.collect(Collectors.toList()).size());
	}
	@Transactional
	public SkillTreeDiagramDTO generateSkillTreeForTeamByGroupID(Long id, List<String> mails) {
		SkillTreeDiagramDTO skillTreeDiagramDTO = new SkillTreeDiagramDTO();
		List<Long> learnedUserSkillIDs = new ArrayList<Long>();
		List<UserSkill> userSkills = userSkillRepository.findRootUserSkillByGroupID(id, true);
		for (UserSkill userSKill : userSkills) {
			SkillTreeObjectDTO object = this.buildSkillTreeObject(
					new UserSkillToSkillTreeUser(userSKill, getColorForRootObjectFromTeamByGroupID(mails, id),isGroupLearnedCompletlyFromTeamByGroupID(mails,id)));
			object.setLearnCoverage(calculateCoverage(this.getAmountOfLearndSkillsFromTeamByGroupID(mails, id),
					this.getAmountOfUserSkillsFromTeamByGroupID(mails, id)));
			skillTreeDiagramDTO.addNode(object);
			for (UserSkill followingUserSkill : userSKill.getFollowingUserSkills()) {
				skillTreeDiagramDTO.addLine(new SkillTreeLinksDTO(String.valueOf(userSKill.getId()),
						String.valueOf(followingUserSkill.getId())));
			}
		}
		for (String mail : mails) {
			if (mail != null || mail != "" || !mail.equalsIgnoreCase("null")) {

				SkillTreeUser user = skillTreeUserRepository.findByMail(mail);
				if (user == null) {
					LOGGER.info("User with mail: {}  does not exist yet - creating it...", mail);
					user = userSkillService.createSkillTreeUser(mail);
				}
				for (UserSkillToSkillTreeUser userSkillToSkillTreeUser : user.getUserSkillToSkillTreeUser()) {
					if (userSkillToSkillTreeUser.getUserSkill().getUserSkillGroup().getId().equals(id)) {
						if (userSkillToSkillTreeUser.getLearnedOn() != null) {
							learnedUserSkillIDs.add(userSkillToSkillTreeUser.getUserSkill().getId());
						}
						if (skillTreeDiagramDTO
								.getNodes().stream().filter(entry -> String
										.valueOf(userSkillToSkillTreeUser.getUserSkill().getId()).equals(entry.getId()))
								.findAny().orElse(null) == null) {
							skillTreeDiagramDTO.addNode(this.buildSkillTreeObject(userSkillToSkillTreeUser));
							for (UserSkill followingUserSkill : userSkillToSkillTreeUser.getUserSkill()
									.getFollowingUserSkills()) {
								skillTreeDiagramDTO.addLine(new SkillTreeLinksDTO(
										String.valueOf(userSkillToSkillTreeUser.getUserSkill().getId()),
										String.valueOf(followingUserSkill.getId())));
							}
						}

					}
				}
			}
		}
		for (SkillTreeObjectDTO entry : skillTreeDiagramDTO.getNodes()) {
			int learnedUserSKillFrequency = Collections.frequency(learnedUserSkillIDs, Long.getLong(entry.getId()));
			if (learnedUserSKillFrequency > 0 && learnedUserSKillFrequency < mails.size()) {
				entry.setBackgroundColor("#e68080");
				entry.setTextColor("#fff");
			} else if (learnedUserSKillFrequency == mails.size()) {
				entry.setBackgroundColor("#c62828");
				entry.setTextColor("#fff");
			} else {
				entry.setBackgroundColor("#c0c0c0");
				entry.setTextColor("#262626");
			}
			entry.setLearnCoverage(
					calculateCoverage(Double.valueOf(learnedUserSKillFrequency), Double.valueOf(mails.size())));
		}
		return skillTreeDiagramDTO;
	}

	private int calculateCoverage(Double numerator, Double denominator) {
		Double percentage = (numerator / denominator) * 100;
		int coverage = (int) Math.round(percentage);
		if (coverage > 100) {
			return 100;
		}
		return coverage;
	}

	private SkillTreeObjectDTO buildSkillTreeObject(UserSkillToSkillTreeUser userSkillToSkillTreeUser) {
		SkillTreeObjectDTO skillTreeObjectDTO = new SkillTreeObjectDTO();
		skillTreeObjectDTO.setId(String.valueOf(userSkillToSkillTreeUser.getUserSkill().getId()));
		skillTreeObjectDTO.setLabel(String.valueOf(userSkillToSkillTreeUser.getUserSkill().getName()));
		skillTreeObjectDTO.setRepeats(userSkillToSkillTreeUser.getRepeats());
		skillTreeObjectDTO.setRequiredRepetitions(userSkillToSkillTreeUser.getUserSkill().getRequiredRepetitions());
		skillTreeObjectDTO.setGroupIcon(userSkillToSkillTreeUser.getUserSkill().getUserSkillGroup().getIcon());
		skillTreeObjectDTO.setRoot(userSkillToSkillTreeUser.getUserSkill().isRoot());
		for (SonarRule rule : userSkillToSkillTreeUser.getUserSkill().getSonarRules()) {
			skillTreeObjectDTO.addRuleKey(rule.getKey(), rule.getName());
		}
		if (userSkillToSkillTreeUser.getSkillTreeUser() != null) {
			if (userSkillToSkillTreeUser.getLearnedOn() != null) {
				skillTreeObjectDTO.setBackgroundColor("#c62828");
				skillTreeObjectDTO.setTextColor("#fff");
			} else if (userSkillToSkillTreeUser.getRepeats() > 0) {
				skillTreeObjectDTO.setBackgroundColor("#e68080");
				skillTreeObjectDTO.setTextColor("#fff");
			} else {
				skillTreeObjectDTO.setBackgroundColor("#c0c0c0");
				skillTreeObjectDTO.setTextColor("#262626");
			}
		} else {
			skillTreeObjectDTO.setBackgroundColor("#c0c0c0");
			skillTreeObjectDTO.setTextColor("#262626");
		}

		return skillTreeObjectDTO;
	}

}