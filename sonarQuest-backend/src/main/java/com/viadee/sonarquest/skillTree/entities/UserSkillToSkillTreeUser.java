package com.viadee.sonarquest.skillTree.entities;

import java.sql.Timestamp;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "User_Skill_To_Skill_Tree_User")
public class UserSkillToSkillTreeUser {
	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "learned_on")
	private Timestamp learnedOn;

	@Column(name = "repeats")
	private int repeats;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_skill_id")
	private UserSkill userSkill;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "skill_tree_user_id")
	private SkillTreeUser skillTreeUser;

	public UserSkillToSkillTreeUser() {
	}

	public UserSkillToSkillTreeUser( Timestamp learnedOn, int repeats, UserSkill userSkill,
			SkillTreeUser skillTreeUser) {
		this.learnedOn = learnedOn;
		this.repeats = repeats;
		this.userSkill = userSkill;
		this.skillTreeUser = skillTreeUser;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof UserSkillToSkillTreeUser))
			return false;
		UserSkillToSkillTreeUser that = (UserSkillToSkillTreeUser) o;
		return Objects.equals(userSkill.getId(), that.userSkill.getId())
				&& Objects.equals(skillTreeUser.getId(), that.skillTreeUser.getId())
				&& Objects.equals(learnedOn, that.learnedOn) && Objects.equals(repeats, that.repeats);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userSkill.getName(), skillTreeUser.getMail(), learnedOn, repeats);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Timestamp getLearnedOn() {
		return learnedOn;
	}

	public void setLearnedOn(Timestamp learnedOn) {
		this.learnedOn = learnedOn;
	}

	public int getRepeats() {
		return repeats;
	}

	public void setRepeats(int repeats) {
		this.repeats = repeats;
	}

	public UserSkill getUserSkill() {
		return userSkill;
	}

	public void setUserSkill(UserSkill userSkill) {
		this.userSkill = userSkill;
	}

	public SkillTreeUser getSkillTreeUser() {
		return skillTreeUser;
	}

	public void setSkillTreeUser(SkillTreeUser skillTreeUser) {
		this.skillTreeUser = skillTreeUser;
	}

}
