package com.example.demo.goal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.goal.GoalDtos.GoalRequest;
import com.example.demo.goal.GoalDtos.GoalResponse;
import com.example.demo.task.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GoalService {

	private final GoalRepository goals;
	private final MilestoneRepository milestones;
	private final TaskRepository tasks;

	@Transactional(readOnly = true)
	public List<GoalResponse> list(GoalStatus status) {
		var result = status != null ? goals.findByStatusOrderByTargetDateAsc(status)
				: goals.findAllByOrderByStatusAscTargetDateAsc();
		return result.stream().map(GoalResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public GoalResponse find(Long id) {
		return GoalResponse.from(get(id));
	}

	public GoalResponse create(GoalRequest req) {
		var goal = new Goal();
		apply(goal, req);
		if (req.milestones() != null) {
			req.milestones().forEach(goal::addMilestone);
		}
		return GoalResponse.from(goals.save(goal));
	}

	public GoalResponse update(Long id, GoalRequest req) {
		var goal = get(id);
		apply(goal, req);
		return GoalResponse.from(goal);
	}

	public void delete(Long id) {
		// Görevler silinmez, yalnızca hedefle bağlantıları kalkar.
		tasks.findByGoalId(id).forEach(t -> t.setGoal(null));
		goals.delete(get(id));
	}

	public GoalResponse addMilestone(Long goalId, String title) {
		var goal = get(goalId);
		goal.addMilestone(title);
		goals.flush();
		return GoalResponse.from(goal);
	}

	public GoalResponse setMilestoneDone(Long milestoneId, boolean done) {
		var m = milestones.findById(milestoneId).orElseThrow(() -> new NotFoundException("Kilometre taşı", milestoneId));
		m.setDone(done);
		var goal = m.getGoal();
		// Tüm adımlar bitince hedef otomatik tamamlanır; geri alınırsa yeniden aktifleşir.
		if (goal.progress() == 100 && goal.getStatus() == GoalStatus.ACTIVE) {
			goal.setStatus(GoalStatus.COMPLETED);
		}
		else if (!done && goal.getStatus() == GoalStatus.COMPLETED) {
			goal.setStatus(GoalStatus.ACTIVE);
		}
		return GoalResponse.from(goal);
	}

	public GoalResponse deleteMilestone(Long milestoneId) {
		var m = milestones.findById(milestoneId).orElseThrow(() -> new NotFoundException("Kilometre taşı", milestoneId));
		var goal = m.getGoal();
		goal.getMilestones().remove(m);
		return GoalResponse.from(goal);
	}

	private Goal get(Long id) {
		return goals.findById(id).orElseThrow(() -> new NotFoundException("Hedef", id));
	}

	private void apply(Goal goal, GoalRequest req) {
		goal.setTitle(req.title());
		goal.setDescription(req.description());
		goal.setTargetDate(req.targetDate());
		if (req.category() != null) {
			goal.setCategory(req.category());
		}
		if (req.status() != null) {
			goal.setStatus(req.status());
		}
	}
}
