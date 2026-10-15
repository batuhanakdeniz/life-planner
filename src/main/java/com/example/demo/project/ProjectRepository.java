package com.example.demo.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findAllByOrderByPositionAscIdAsc();

	Optional<Project> findBySlugIgnoreCase(String slug);
}
