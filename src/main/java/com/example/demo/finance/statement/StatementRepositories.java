package com.example.demo.finance.statement;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {

	List<SpendingCategory> findAllByOrderByPositionAscIdAsc();

	Optional<SpendingCategory> findByNameIgnoreCase(String name);
}

interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

	Optional<CategoryRule> findByPatternAndUserDefined(String pattern, boolean userDefined);

	List<CategoryRule> findByUserDefinedTrueOrderByCreatedAtDesc();
}

interface StatementRepository extends JpaRepository<Statement, Long> {

	List<Statement> findAllByOrderByPeriodEndDescIdDesc();

	Optional<Statement> findByFileId(Long fileId);
}

interface StatementTransactionRepository extends JpaRepository<StatementTransaction, Long> {

	List<StatementTransaction> findByCategorySourceNot(StatementTransaction.CategorySource source);

	List<StatementTransaction> findByRuleId(Long ruleId);

	long countByCategoryId(Long categoryId);
}
