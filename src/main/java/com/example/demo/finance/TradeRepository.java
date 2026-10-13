package com.example.demo.finance;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

	@EntityGraph(attributePaths = "asset")
	List<Trade> findAllByOrderByDateDescIdDesc();

	@EntityGraph(attributePaths = "asset")
	List<Trade> findByAssetIdOrderByDateDescIdDesc(Long assetId);

	List<Trade> findByAssetIdOrderByDateAscIdAsc(Long assetId);

	boolean existsByAssetId(Long assetId);

	List<Trade> findByRateEstimatedTrue();
}
