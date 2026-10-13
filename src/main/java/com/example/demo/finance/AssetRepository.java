package com.example.demo.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

	List<Asset> findAllByOrderByTypeAscNameAsc();

	boolean existsBySymbolIgnoreCase(String symbol);
}
